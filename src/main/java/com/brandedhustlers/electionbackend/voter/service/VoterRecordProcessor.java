package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import com.brandedhustlers.electionbackend.voter.repository.VoterImportJobRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Processes individual voter records, each in its own REQUIRES_NEW transaction.
 *
 * <p>Must be a separate Spring bean — never call these methods via {@code this.xxx()}
 * from within the same class, or Spring's AOP proxy will be bypassed and the
 * transaction boundary won't apply.</p>
 */
@Service
@Slf4j
public class VoterRecordProcessor {

    private final VoterRepository voterRepository;
    private final VoterImportJobRepository jobRepository;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final AttributeExtractor attributeExtractor;

    public VoterRecordProcessor(VoterRepository voterRepository,
                                VoterImportJobRepository jobRepository,
                                AppUserRepository appUserRepository,
                                OrganizationRepository organizationRepository,
                                AttributeExtractor attributeExtractor) {
        this.voterRepository = voterRepository;
        this.jobRepository = jobRepository;
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.attributeExtractor = attributeExtractor;
    }

    /**
     * Insert a single voter in its own isolated transaction.
     *
     * @return {@code null} on success, or a human-readable error message on failure.
     *         The caller should treat a non-null return as a skipped record, not a crash.
     */
    /**
     * Insert a single voter in its own isolated transaction.
     *
     * <p><strong>Important:</strong> {@link DataIntegrityViolationException} must NOT be
     * caught inside this method.  When Hibernate encounters a DB constraint violation
     * during {@code saveAndFlush}, it marks the current JPA session rollback-only
     * <em>before</em> throwing.  Catching the exception here and returning normally would
     * cause Spring to attempt a commit on a rollback-only transaction, which throws
     * {@code UnexpectedRollbackException} and aborts the entire import job.
     *
     * <p>Let the exception propagate out; the caller wraps this call in its own try-catch
     * outside the transaction boundary, so the rollback is clean and the job continues.
     *
     * @return {@code null} on success. Returns a validation error string (pre-DB check)
     *         if the record is invalid. DB constraint violations are propagated as exceptions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String insertVoter(CreateVoterCmd cmd, UUID userId, UUID orgId, String fileRef) {
        // ── 1. Hard validation before touching the DB ──────────────────────
        String validationError = validate(cmd);
        if (validationError != null) {
            return validationError;
        }

        Map<String, Object> attributes = null;
        if (cmd.attributes() != null && !cmd.attributes().isEmpty()) {
            attributes = attributeExtractor.extractAttributes(
                    cmd.nationalId().trim(),
                    cmd.phone().trim(),
                    cmd.email().trim().toLowerCase(),
                    cmd.attributes()
            );
        }

        Voter voter = Voter.builder()
                // Use getReferenceById to avoid loading the full entity on every row
                .organization(organizationRepository.getReferenceById(orgId))
                .name(cmd.name().trim())
                .phone(cmd.phone().trim())
                .email(cmd.email().trim().toLowerCase())
                .nationalId(cmd.nationalId().trim())
                .voterStatus(VoterStatus.ACTIVE)
                .createdBy(appUserRepository.getReferenceById(userId))
                .attributes(attributes)
                .attributesSource("BULK_IMPORT_" + fileRef)
                .attributesUpdatedAt(Timestamp.from(Instant.now()))
                .build();

        voterRepository.saveAndFlush(voter);
        return null; // success — DB constraint violations propagate as DataIntegrityViolationException
    }

    /**
     * Classifies a {@link DataIntegrityViolationException} from an {@code insertVoter} call
     * into a human-readable skip message.  Call this from the <em>caller's</em> catch block,
     * not inside the transactional method itself.
     */
    public String classifyInsertError(DataIntegrityViolationException ex, CreateVoterCmd cmd) {
        return classifyConstraintViolation(ex, cmd);
    }

    // ── Job lifecycle helpers — each gets its own transaction ──────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobRunning(UUID jobId, int total) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("RUNNING");
            job.setStartedAt(new Timestamp(System.currentTimeMillis()));
            job.setTotal(total);
            jobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobProgress(UUID jobId, int processed, int failed) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setProcessed(processed);
            job.setFailed(failed);
            jobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(UUID jobId, String status, int processed, int failed) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setProcessed(processed);
            job.setFailed(failed);
            job.setFinishedAt(new Timestamp(System.currentTimeMillis()));
            jobRepository.save(job);
        });
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Pure in-memory validation — no DB calls.
     * Catches problems that would otherwise reach the DB and produce unhelpful errors.
     *
     * @return {@code null} if valid, or a descriptive error string.
     */
    private String validate(CreateVoterCmd cmd) {
        if (cmd.name() == null || cmd.name().isBlank())
            return "Name is required";
        if (cmd.name().trim().length() > 255)
            return "Name exceeds 255 characters";

        if (cmd.email() == null || cmd.email().isBlank())
            return "Email is required";
        if (cmd.email().trim().length() > 255)
            return "Email exceeds 255 characters";
        if (!cmd.email().contains("@"))
            return "Invalid email format: " + cmd.email().trim();

        if (cmd.phone() == null || cmd.phone().isBlank())
            return "Phone is required";
        if (cmd.phone().trim().length() > 50)
            return "Phone number too long (max 50 chars): " + cmd.phone().trim().substring(0, 20) + "…";

        if (cmd.nationalId() == null || cmd.nationalId().isBlank())
            return "National ID is required";
        if (cmd.nationalId().trim().length() > 100)
            return "National ID too long (max 100 chars)";

        return null;
    }

    /**
     * Inspects the constraint violation to produce a readable duplicate-field message.
     * Falls back to the raw DB message if the constraint name is unrecognised.
     */
    private String classifyConstraintViolation(DataIntegrityViolationException ex, CreateVoterCmd cmd) {
        String detail = ex.getCause() != null
                ? ex.getCause().getMessage()
                : (ex.getMessage() != null ? ex.getMessage() : "");
        String lower = detail.toLowerCase();

        if (lower.contains("email"))
            return "Duplicate email: " + cmd.email().trim().toLowerCase();
        if (lower.contains("phone"))
            return "Duplicate phone: " + cmd.phone().trim();
        if (lower.contains("national_id") || lower.contains("nationalid"))
            return "Duplicate national ID: " + cmd.nationalId().trim();

        // Unknown constraint — surface the most specific cause message
        return "Constraint violation for voter '" + cmd.email() + "': "
                + ex.getMostSpecificCause().getMessage();
    }
}
