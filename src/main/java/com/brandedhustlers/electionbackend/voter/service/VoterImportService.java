package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.voter.domain.VoterImportJob;
import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import com.brandedhustlers.electionbackend.voter.repository.VoterImportJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Public API for the voter import subsystem.
 *
 * <p>Keeps its own responsibility small: authenticate, authorise, create the
 * job record, then hand off to {@link VoterImportRunner} for the heavy lifting.
 * The runner lives in a separate bean so that its {@code @Async} annotation is
 * applied by the Spring AOP proxy (self-invocation would bypass it).</p>
 */
@Service
@Slf4j
public class VoterImportService implements IVoterImportService {

    private final VoterImportJobRepository jobRepository;
    private final VoterImportRunner importRunner;
    private final GetUser getUser;
    private final Util util;
    private final AuditEmitter emitter;

    public VoterImportService(VoterImportJobRepository jobRepository,
                              VoterImportRunner importRunner,
                              GetUser getUser,
                              Util util, AuditEmitter emitter) {
        this.jobRepository = jobRepository;
        this.importRunner = importRunner;
        this.getUser = getUser;
        this.util = util;
        this.emitter = emitter;
    }

    @Override
    @Transactional
    public UUID startImport(String fileRef) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(actor, PermissionType.IMPORT_VOTERS);

        UUID jobId = UUID.randomUUID();
        UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
        UUID userId = actor.getAppUserId();

        log.info("Starting import job {} for org {} by user {}", jobId, orgId, userId);

        VoterImportJob job = VoterImportJob.builder()
                .id(jobId)
                .orgId(orgId)
                .fileRef(fileRef)
                .total(0)
                .processed(0)
                .failed(0)
                .status("PENDING")
                .build();

        // Flush immediately so the async thread can find the job by ID
        jobRepository.saveAndFlush(job);

        // Delegate to a separate bean — this call goes through the Spring proxy,
        // so @Async is actually applied and the method runs on taskExecutor.
        importRunner.run(jobId, fileRef, userId, orgId);
        emitter.emit(
                UUID.randomUUID(),
                "voter.import-job",
                Instant.now(),
                orgId,
                "New Import Job",
                userId,
                actor.getFirstName() + " " + actor.getLastName(),
                actor.getUserRole().name(),
                actor.getUserRole().name(),
                "Import Job",
                job.getId(),
                null,
                "Import Job",
                null,
                null,
                null,
                "INFO",
                1
        );

        return jobId;
    }

    @Override
    public ImportStatusDTO status(UUID jobId) {
        VoterImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Import job not found: " + jobId));

        AppUser actor = getUser.getCurrentAuthenticatedUser();
        if (!util.isPlatformAdmin(actor)) {
            UUID orgId = getUser.getCurrentAuthenticatedUserOrgId();
            if (!job.getOrgId().equals(orgId)) {
                throw new AccessDeniedException("Unauthorised access to import job");
            }
        }

        return new ImportStatusDTO(
                job.getId(),
                job.getOrgId(),
                job.getStatus(),
                job.getTotal(),
                job.getProcessed(),
                job.getFailed()
        );
    }
}
