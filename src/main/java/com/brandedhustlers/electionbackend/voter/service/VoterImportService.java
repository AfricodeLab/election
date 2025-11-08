package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.domain.VoterImportJob;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.repository.VoterImportJobRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VoterImportService implements IVoterImportService {

    private final VoterImportJobRepository jobRepository;
    private final VoterRepository voterRepository;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final GetUser getUser;

    public VoterImportService(VoterImportJobRepository jobRepository,
                              VoterRepository voterRepository,
                              HttpServletRequest httpServletRequest,
                              JWTService jwtService,
                              AppUserRepository appUserRepository,
                              GetUser getUser) {
        this.jobRepository = jobRepository;
        this.voterRepository = voterRepository;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
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
            boolean isFirstRow = true;

            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue; // Skip header
                }

                if (isRowEmpty(row)) {
                    continue;
                }

                String name = getCellValueAsString(row.getCell(0));
                String phone = getCellValueAsString(row.getCell(1));
                String email = getCellValueAsString(row.getCell(2));
                String nationalId = getCellValueAsString(row.getCell(3));

                if (name != null && phone != null && email != null && nationalId != null) {
                    voters.add(new CreateVoterCmd(name, phone, email, nationalId));
                }
            }
        }

        return voters;
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


        // Get current user - adjust based on your security context
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

                Voter voter = Voter.builder()
                        .organization(currentUser.getAppUserOrganization())
                        .name(cmd.name())
                        .phone(cmd.phone())
                        .email(cmd.email())
                        .nationalId(cmd.nationalId())
                        .voterStatus(VoterStatus.ACTIVE)
                        .createdBy(currentUser)
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