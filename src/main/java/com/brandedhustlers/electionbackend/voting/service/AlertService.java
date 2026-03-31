package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AlertService {
    private final MessagingService messagingService;
    private final AppUserRepository appUserRepository;

    private static final Set<UserRole> ALERT_ROLES =
            Set.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.ORG_ADMIN);

    public AlertService(MessagingService messagingService,
                        AppUserRepository appUserRepository) {
        this.messagingService = messagingService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Alert org admins when vote tampering is detected.
     * Emails every SUPER_ADMIN/ADMIN/ORG_ADMIN in the affected org.
     */
    public void notifyTamperingDetected(List<TamperingLog> incidents) {
        if (incidents == null || incidents.isEmpty()) return;

        long critical = incidents.stream()
                .filter(i -> "ARCHIVE_TAMPERED".equals(i.getIncidentType()))
                .count();
        long restored = incidents.stream()
                .filter(i -> "RESTORED".equals(i.getIncidentType()))
                .count();

        // Collect distinct affected orgs from the incident list
        incidents.stream()
                .map(TamperingLog::getOrgId)
                .distinct()
                .forEach(orgId -> {
                    String severity = critical > 0 ? "CRITICAL" : "WARNING";
                    String summary = buildTamperingSummary(orgId, critical, restored);
                    log.error("Tampering alert dispatched for org {}: {} critical, {} restored",
                            orgId, critical, restored);
                    notifyOrgAdmins(orgId, severity, summary);
                });
    }

    /**
     * Alert admins when a candidate name changes while voting is live.
     */
    public void notifyConfigChange(Election election, Candidate candidate, String newName) {
        UUID orgId = election.getOrganization() == null ? null
                : election.getOrganization().getOrganizationId();
        String summary = String.format(
                "Candidate '%s' (ID: %s) was renamed to '%s' while election '%s' (ID: %s) was LIVE. "
                        + "This may indicate an unauthorised configuration change.",
                candidate.getCandidateName(), candidate.getCandidateId(),
                newName, election.getElectionTitle(), election.getElectionId());

        log.warn("CONFIG CHANGE ALERT: Election {} — Candidate name changed to '{}'",
                election.getElectionId(), newName);

        if (orgId != null) {
            notifyOrgAdmins(orgId, "WARNING", summary);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void notifyOrgAdmins(UUID orgId, String severity, String summary) {
        List<AppUser> admins = appUserRepository.getUsersInOrg(orgId).stream()
                .filter(u -> ALERT_ROLES.contains(u.getUserRole()))
                .toList();

        if (admins.isEmpty()) {
            log.warn("No admins found for org {} to receive {} alert", orgId, severity);
            return;
        }

        admins.forEach(admin ->
                messagingService.sendTamperingAlert(
                        admin.getAppUserEmail(),
                        admin.getFirstName(),
                        severity,
                        summary));
    }

    private String buildTamperingSummary(UUID orgId, long critical, long restored) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tampering was detected in your organisation's election data.<br/><br/>");
        if (critical > 0) {
            sb.append("<strong>").append(critical)
              .append(" vote(s) could not be restored</strong> — both the vote record and its archive were corrupted.<br/>");
        }
        if (restored > 0) {
            sb.append(restored)
              .append(" vote(s) were tampered with but <strong>successfully restored</strong> from the archive.<br/>");
        }
        sb.append("<br/>Organisation ID: ").append(orgId);
        return sb.toString();
    }
}