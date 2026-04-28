package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Owns the {@code @Async} boundary for voter imports.
 *
 * <p>Must be a separate Spring bean from {@link VoterImportService} so that
 * the call to {@link #run} goes through the AOP proxy and {@code @Async} is
 * actually applied. Calling an async method via {@code this.run()} from the
 * same class silently bypasses the proxy.</p>
 *
 * <p>This class is intentionally NOT {@code @Transactional}. All DB work is
 * delegated to {@link VoterRecordProcessor}, which wraps each unit of work in
 * its own {@code REQUIRES_NEW} transaction. That gives us per-record isolation:
 * one bad row fails alone — it cannot poison the rest of the import.</p>
 */
@Service
@Slf4j
public class VoterImportRunner {

    private final VoterRecordProcessor recordProcessor;

    public VoterImportRunner(VoterRecordProcessor recordProcessor) {
        this.recordProcessor = recordProcessor;
    }

    /**
     * Runs the full import pipeline asynchronously.
     *
     * <p>Call this from another Spring bean (never {@code this.run()}) so that
     * the {@code @Async} annotation is honoured by the proxy.</p>
     *
     * @param jobId   the already-persisted import-job ID
     * @param fileRef path to the uploaded CSV or Excel file
     * @param userId  ID of the admin who initiated the import
     * @param orgId   organisation the voters will belong to
     */
    @Async("taskExecutor")
    public void run(UUID jobId, String fileRef, UUID userId, UUID orgId) {
        log.info("Import runner started — job={} user={} org={}", jobId, userId, orgId);

        int processed = 0;
        int failed = 0;

        try {
            // ── 1. Parse file (pure I/O, no transaction needed) ───────────
            List<CreateVoterCmd> voters = parseFile(fileRef);
            log.info("Parsed {} voter records for job {}", voters.size(), jobId);

            // ── 2. Mark job as running with total count ────────────────────
            recordProcessor.markJobRunning(jobId, voters.size());

            if (voters.isEmpty()) {
                log.warn("No voter records found in file: {}", fileRef);
                recordProcessor.finalizeJob(jobId, "DONE", 0, 0);
                return;
            }

            // ── 3. Process each voter in its own isolated transaction ──────
            for (int i = 0; i < voters.size(); i++) {
                CreateVoterCmd cmd = voters.get(i);

                // insertVoter() runs in PROPAGATION_REQUIRES_NEW — it gets its own
                // transaction that is fully committed (or rolled back) before this
                // loop continues.  Catching DataIntegrityViolationException HERE
                // (outside the transactional method) is required: if we caught it
                // inside the method, Hibernate would have already marked the session
                // rollback-only, causing an UnexpectedRollbackException on commit.
                try {
                    String error = recordProcessor.insertVoter(cmd, userId, orgId, fileRef);
                    if (error == null) {
                        processed++;
                    } else {
                        // Pre-DB validation failure (blank field, bad format, etc.)
                        failed++;
                        log.warn("Job {} — row {} skipped [{}]: {}", jobId, i + 2, cmd.email(), error);
                    }
                } catch (DataIntegrityViolationException ex) {
                    // Duplicate email / phone / nationalId — skip this row and continue
                    failed++;
                    String reason = recordProcessor.classifyInsertError(ex, cmd);
                    log.warn("Job {} — row {} skipped [{}]: {}", jobId, i + 2, cmd.email(), reason);
                }

                // Publish progress every 50 records so the status endpoint stays fresh
                if ((i + 1) % 50 == 0) {
                    recordProcessor.updateJobProgress(jobId, processed, failed);
                    log.info("Job {} progress: {}/{} done, {} failed",
                            jobId, processed + failed, voters.size(), failed);
                }
            }

            // ── 4. Finalize ────────────────────────────────────────────────
            recordProcessor.finalizeJob(jobId, "DONE", processed, failed);
            log.info("Job {} complete — {} inserted, {} failed out of {}",
                    jobId, processed, failed, voters.size());

        } catch (Exception e) {
            log.error("Job {} failed unexpectedly during import", jobId, e);
            recordProcessor.finalizeJob(jobId, "FAILED", processed, failed);
        }
    }

    // ── File parsing ───────────────────────────────────────────────────────
    // Pure I/O — no Spring beans, no transactions, no DB calls.

    private List<CreateVoterCmd> parseFile(String fileRef) throws IOException {
        Path filePath = Paths.get(fileRef);

        if (!Files.exists(filePath)) {
            throw new IOException("Import file not found: " + fileRef);
        }

        String name = filePath.getFileName().toString().toLowerCase();

        if (name.endsWith(".csv")) {
            return parseCsv(filePath);
        } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return parseExcel(filePath);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file format '" + name + "'. Use CSV or Excel (.xlsx / .xls).");
        }
    }

    private List<CreateVoterCmd> parseCsv(Path filePath) throws IOException {
        List<CreateVoterCmd> voters = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            // ── Read and parse the header row first ────────────────────────
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV file is empty");
            }

            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                // Normalise: lowercase, strip spaces — same as Excel parser
                colMap.put(headers[i].trim().toLowerCase().replaceAll("\\s+", ""), i);
            }

            log.debug("CSV columns detected: {}", colMap.keySet());

            if (!colMap.containsKey("name") || !colMap.containsKey("phone")
                    || !colMap.containsKey("email") || !colMap.containsKey("nationalid")) {
                throw new IOException(
                        "CSV missing required columns. Expected headers: name, phone, email, nationalId. Found: "
                                + colMap.keySet());
            }

            int iName     = colMap.get("name");
            int iPhone    = colMap.get("phone");
            int iEmail    = colMap.get("email");
            int iNational = colMap.get("nationalid");

            // ── Process data rows ──────────────────────────────────────────
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] fields = parseCsvLine(line);
                int len = fields.length;

                String name     = len > iName     ? fields[iName].trim()     : null;
                String phone    = len > iPhone    ? fields[iPhone].trim()    : null;
                String email    = len > iEmail    ? fields[iEmail].trim()    : null;
                String national = len > iNational ? fields[iNational].trim() : null;

                // Skip rows where any required column is missing or blank
                if (isBlankOrNull(name) || isBlankOrNull(phone)
                        || isBlankOrNull(email) || isBlankOrNull(national)) {
                    continue;
                }

                // Extra columns become attributes (optional)
                Map<String, Object> attrs = extractCsvAttributes(fields, colMap);

                voters.add(new CreateVoterCmd(name, phone, email, national,
                        attrs.isEmpty() ? null : attrs));
            }
        }

        log.info("Parsed {} rows from CSV", voters.size());
        return voters;
    }

    private boolean isBlankOrNull(String s) {
        return s == null || s.isBlank();
    }

    private Map<String, Object> extractCsvAttributes(String[] fields, Map<String, Integer> colMap) {
        Map<String, Object> attrs = new HashMap<>();

        if (colMap.containsKey("age")) {
            String v = safeGet(fields, colMap.get("age"));
            if (v != null) {
                try { attrs.put("age", Integer.parseInt(v)); }
                catch (NumberFormatException e) { log.warn("Invalid age value: {}", v); }
            }
        }
        if (colMap.containsKey("region")) {
            String v = safeGet(fields, colMap.get("region"));
            if (v != null) attrs.put("region", v);
        }
        if (colMap.containsKey("citizen")) {
            String v = safeGet(fields, colMap.get("citizen"));
            if (v != null) attrs.put("citizen",
                    "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v));
        }
        if (colMap.containsKey("role")) {
            String v = safeGet(fields, colMap.get("role"));
            if (v != null) attrs.put("role", v);
        }
        if (colMap.containsKey("employmentstatus")) {
            String v = safeGet(fields, colMap.get("employmentstatus"));
            if (v != null) attrs.put("employmentStatus", v);
        }

        return attrs;
    }

    private String safeGet(String[] fields, int index) {
        if (index >= fields.length) return null;
        String v = fields[index].trim();
        return v.isBlank() ? null : v;
    }

    /** Handles quoted fields correctly. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private List<CreateVoterCmd> parseExcel(Path filePath) throws IOException {
        List<CreateVoterCmd> voters = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IOException("Excel file has no header row");
            }

            Map<String, Integer> colMap = buildColumnMap(headerRow);

            if (!colMap.containsKey("name") || !colMap.containsKey("phone")
                    || !colMap.containsKey("email") || !colMap.containsKey("nationalid")) {
                throw new IOException(
                        "Excel missing required columns. Expected: name, phone, email, nationalId");
            }

            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) { firstRow = false; continue; }
                if (isRowEmpty(row)) continue;

                String rName     = getCellValue(row, colMap.get("name"));
                String rPhone    = getCellValue(row, colMap.get("phone"));
                String rEmail    = getCellValue(row, colMap.get("email"));
                String rNational = getCellValue(row, colMap.get("nationalid"));

                if (rName == null || rPhone == null || rEmail == null || rNational == null) continue;

                Map<String, Object> attrs = extractExcelAttributes(row, colMap);

                voters.add(new CreateVoterCmd(
                        rName, rPhone, rEmail, rNational,
                        attrs.isEmpty() ? null : attrs
                ));
            }
        }

        log.info("Parsed {} rows from Excel", voters.size());
        return voters;
    }

    private Map<String, Object> extractExcelAttributes(Row row, Map<String, Integer> colMap) {
        Map<String, Object> attrs = new HashMap<>();

        if (colMap.containsKey("age")) {
            String v = getCellValue(row, colMap.get("age"));
            if (v != null) {
                try { attrs.put("age", Integer.parseInt(v)); }
                catch (NumberFormatException e) { log.warn("Invalid age value: {}", v); }
            }
        }
        if (colMap.containsKey("region")) {
            String v = getCellValue(row, colMap.get("region"));
            if (v != null) attrs.put("region", v);
        }
        if (colMap.containsKey("citizen")) {
            String v = getCellValue(row, colMap.get("citizen"));
            if (v != null) attrs.put("citizen",
                    "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v));
        }
        if (colMap.containsKey("role")) {
            String v = getCellValue(row, colMap.get("role"));
            if (v != null) attrs.put("role", v);
        }
        if (colMap.containsKey("employmentstatus")) {
            String v = getCellValue(row, colMap.get("employmentstatus"));
            if (v != null) attrs.put("employmentStatus", v);
        }

        return attrs;
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> colMap = new HashMap<>();
        for (Cell cell : headerRow) {
            String val = getCellValueAsString(cell);
            if (val != null) {
                colMap.put(val.toLowerCase().replaceAll("\\s+", ""), cell.getColumnIndex());
            }
        }
        log.debug("Detected Excel columns: {}", colMap.keySet());
        return colMap;
    }

    private String getCellValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        return getCellValueAsString(row.getCell(colIndex));
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 4; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = getCellValueAsString(cell);
                if (v != null && !v.isBlank()) return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default      -> null;
        };
    }

    private String formatNumeric(double v) {
        return (v == (long) v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
