package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
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
    private final FeatureFlagService featureFlags;

    private static final Set<UserRole> ALERT_ROLES =
            Set.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.ORG_ADMIN);

    public AlertService(MessagingService messagingService,
                        AppUserRepository appUserRepository,
                        FeatureFlagService featureFlags) {
        this.messagingService = messagingService;
        this.appUserRepository = appUserRepository;
        this.featureFlags = featureFlags;
    }

    /**
     * Alert org admins when vote tampering is detected.
     * Emails every SUPER_ADMIN/ADMIN/ORG_ADMIN in the affected org.
     */
    public void notifyTamperingDetected(List<TamperingLog> incidents) {
        if (incidents == null || incidents.isEmpty()) return;

        // Group incidents by org so each org only receives counts for its own data.
        // Computing totals across all orgs first then reusing them per-org would send
        // one org's severity/counts into another org's alert email.
        incidents.stream()
                .collect(java.util.stream.Collectors.groupingBy(TamperingLog::getOrgId))
                .forEach((orgId, orgIncidents) -> {
                    long critical = orgIncidents.stream()
                            .filter(i -> "ARCHIVE_TAMPERED".equals(i.getIncidentType()))
                            .count();
                    long restored = orgIncidents.stream()
                            .filter(i -> "RESTORED".equals(i.getIncidentType()))
                            .count();

                    String severity = critical > 0 ? "CRITICAL" : "WARNING";
                    String summary = buildTamperingSummary(orgId, critical, restored);
                    // Log "attempting" not "dispatched" — notifyOrgAdmins checks the feature
                    // flag and admin list before anything is actually sent.
                    log.warn("Tampering alert attempting for org {}: {} critical, {} restored",
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
        if (!featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)) {
            log.warn("TAMPER_ALERTS not enabled for org {} — {} alert suppressed. Summary: {}",
                    orgId, severity, summary);
            return;
        }

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
        // Returns plain text — MessagingService.sendTamperingAlert() HTML-escapes this string
        // before embedding it in the email body, so HTML tags here would be rendered literally.
        StringBuilder sb = new StringBuilder();
        sb.append("Tampering was detected in your organisation's election data.\n\n");
        if (critical > 0) {
            sb.append(critical)
              .append(" vote(s) could not be restored — both the vote record and its archive were corrupted.\n");
        }
        if (restored > 0) {
            sb.append(restored)
              .append(" vote(s) were tampered with but successfully restored from the archive.\n");
        }
        sb.append("\nOrganisation ID: ").append(orgId);
        return sb.toString();
    }
}