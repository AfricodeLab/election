package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.domain.VoterImportJob;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.repository.VoterImportJobRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class VoterImportService implements IVoterImportService {

    private final VoterImportJobRepository jobRepository;
    private final VoterRepository voterRepository;
    private final AttributeExtractor attributeExtractor;
    private final GetUser getUser;

    public VoterImportService(VoterImportJobRepository jobRepository,
                              VoterRepository voterRepository,
                              AttributeExtractor attributeExtractor,
                              GetUser getUser) {
        this.jobRepository = jobRepository;
        this.voterRepository = voterRepository;
        this.attributeExtractor = attributeExtractor;
        this.getUser = getUser;
    }

    @Override
    @Transactional
    public UUID startImport(String fileRef) {
        UUID jobId = UUID.randomUUID();
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();

        // Create import job
        VoterImportJob job = VoterImportJob.builder()
                .id(jobId)
                .orgId(orgId)
                .fileRef(fileRef)
                .total(0)
                .processed(0)
                .failed(0)
                .status("PENDING")
                .build();

        jobRepository.save(job);

        // Process asynchronously
        processImportAsync(jobId, fileRef);

        return jobId;
    }

    @Override
    public ImportStatusDTO status(UUID jobId) {
        VoterImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();

        if (!job.getOrgId().equals(orgId)) {
            throw new RuntimeException("Unauthorized access to job");
        }

        return new ImportStatusDTO(
                job.getId(),
                job.getStatus(),
                job.getTotal(),
                job.getProcessed(),
                job.getFailed()
        );
    }

    @Async
    protected void processImportAsync(UUID jobId, String fileRef) {
        VoterImportJob job = jobRepository.findById(jobId)
                .orElseThrow();

        try {
            job.setStatus("RUNNING");
            job.setStartedAt(new Timestamp(System.currentTimeMillis()));
            jobRepository.save(job);

            List<CreateVoterCmd> voters = parseFile(fileRef);
            job.setTotal(voters.size());
            jobRepository.save(job);

            processVoters(job, voters);

            job.setStatus("DONE");
            job.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            jobRepository.save(job);

        } catch (Exception e) {
            log.error("Import failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            jobRepository.save(job);
        }
    }

    private List<CreateVoterCmd> parseFile(String fileRef) throws IOException {
        Path filePath = Paths.get(fileRef);
        String fileName = filePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".csv")) {
            return parseCsv(filePath);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseExcel(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Use CSV or Excel files.");
        }
    }

    private List<CreateVoterCmd> parseCsv(Path filePath) throws IOException {
        List<CreateVoterCmd> voters = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }

                String[] fields = parseCsvLine(line);
                if (fields.length >= 4) {
                    voters.add(new CreateVoterCmd(
                            fields[0].trim(),
                            fields[1].trim(),
                            fields[2].trim(),
                            fields[3].trim()
                    ));
                }
            }
        }

        return voters;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        return fields.toArray(new String[0]);
    }

    private List<CreateVoterCmd> parseExcel(Path filePath) throws IOException {
        List<CreateVoterCmd> voters = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            /**
             * NEW: Detect which columns are present.
             * Expected columns:
             * - Required: name, phone, email, nationalId
             * - Optional: age, region, citizen, role, employmentStatus, etc.
             */
            Map<String, Integer> columnMap = buildColumnMap(headerRow);

            boolean isFirstRow = true;
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue; // Skip header
                }

                if (isRowEmpty(row)) {
                    continue;
                }

                String name = getCellValue(row, columnMap.get("name"));
                String phone = getCellValue(row, columnMap.get("phone"));
                String email = getCellValue(row, columnMap.get("email"));
                String nationalId = getCellValue(row, columnMap.get("nationalId"));

                if (name != null && phone != null && email != null && nationalId != null) {
                    /**
                     * NEW: Extract optional attribute columns.
                     */
                    Map<String, Object> attributes = new HashMap<>();

                    // Age
                    if (columnMap.containsKey("age")) {
                        String ageStr = getCellValue(row, columnMap.get("age"));
                        if (ageStr != null) {
                            try {
                                attributes.put("age", Integer.parseInt(ageStr));
                            } catch (NumberFormatException e) {
                                log.warn("Invalid age value: {}", ageStr);
                            }
                        }
                    }

                    // Region
                    if (columnMap.containsKey("region")) {
                        String region = getCellValue(row, columnMap.get("region"));
                        if (region != null) {
                            attributes.put("region", region);
                        }
                    }

                    // Citizen (boolean)
                    if (columnMap.containsKey("citizen")) {
                        String citizenStr = getCellValue(row, columnMap.get("citizen"));
                        if (citizenStr != null) {
                            attributes.put("citizen",
                                    "true".equalsIgnoreCase(citizenStr) ||
                                            "yes".equalsIgnoreCase(citizenStr) ||
                                            "1".equals(citizenStr)
                            );
                        }
                    }

                    // Role
                    if (columnMap.containsKey("role")) {
                        String role = getCellValue(row, columnMap.get("role"));
                        if (role != null) {
                            attributes.put("role", role);
                        }
                    }

                    // Employment Status
                    if (columnMap.containsKey("employmentStatus")) {
                        String status = getCellValue(row, columnMap.get("employmentStatus"));
                        if (status != null) {
                            attributes.put("employmentStatus", status);
                        }
                    }

                    voters.add(new CreateVoterCmd(
                            name,
                            phone,
                            email,
                            nationalId,
                            attributes.isEmpty() ? null : attributes
                    ));
                }
            }
        }

        return voters;
    }

    /**
     * Build a map of column name → column index.
     * Makes it easy to check which columns are present.
     */
    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        if (headerRow == null) {
            return columnMap;
        }

        for (Cell cell : headerRow) {
            String columnName = getCellValueAsString(cell);
            if (columnName != null) {
                // Normalize: lowercase, no spaces
                String normalized = columnName.toLowerCase().replaceAll("\\s+", "");
                columnMap.put(normalized, cell.getColumnIndex());
            }
        }

        log.info("Detected columns: {}", columnMap.keySet());
        return columnMap;
    }

    /**
     * Get cell value at specific column index (handles null safely).
     */
    private String getCellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        return getCellValueAsString(cell);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < 4; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Format as string without decimal for IDs
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    }
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    @Transactional
    protected void processVoters(VoterImportJob job, List<CreateVoterCmd> voterCmds) {
        AppUser currentUser = getCurrentUser();

        for (CreateVoterCmd cmd : voterCmds) {
            try {
                // Check for duplicates
                if (voterRepository.existsByEmail(cmd.email()) ||
                        voterRepository.existsByPhone(cmd.phone()) ||
                        voterRepository.existsByNationalId(cmd.nationalId())) {

                    job.setFailed(job.getFailed() + 1);
                    log.warn("Duplicate voter: {}", cmd.email());
                    continue;
                }

                /**
                 * NEW: Extract attributes from voter data.
                 * This merges:
                 * - Attributes from CSV/Excel (if provided)
                 * - Auto-derived attributes (age from ID, region from phone)
                 */
                Map<String, Object> attributes = attributeExtractor.extractAttributes(
                        cmd.nationalId(),
                        cmd.phone(),
                        cmd.email(),
                        cmd.attributes()
                );

                Voter voter = Voter.builder()
                        .organization(currentUser.getAppUserOrganization())
                        .name(cmd.name())
                        .phone(cmd.phone())
                        .email(cmd.email())
                        .nationalId(cmd.nationalId())
                        .voterStatus(VoterStatus.ACTIVE)
                        .createdBy(currentUser)
                        .attributes(attributes)
                        .attributesSource("BULK_IMPORT_" + job.getFileRef())
                        .attributesUpdatedAt(Timestamp.from(Instant.now()))
                        .build();

                voterRepository.save(voter);
                job.setProcessed(job.getProcessed() + 1);

                // Save progress every 100 records
                if (job.getProcessed() % 100 == 0) {
                    jobRepository.save(job);
                }

            } catch (Exception e) {
                log.error("Failed to import voter: {}", cmd.email(), e);
                job.setFailed(job.getFailed() + 1);
            }
        }

        jobRepository.save(job);
    }

    private AppUser getCurrentUser() {
        return getUser.getCurrentAuthenticatedUser();
    }
}