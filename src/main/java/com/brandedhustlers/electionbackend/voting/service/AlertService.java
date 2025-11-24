package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AlertService {
    private final MessagingService messagingService;

    public AlertService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    /**
     * Alert admins when tampering is detected.
     */
    public void notifyTamperingDetected(List<TamperingLog> incidents) {
        long critical = incidents.stream()
                .filter(i -> "ARCHIVE_TAMPERED".equals(i.getIncidentType()))
                .count();
        long restored = incidents.stream()
                .filter(i -> "RESTORED".equals(i.getIncidentType()))
                .count();

        if (critical > 0) {
            log.error("CRITICAL ALERT: {} votes could not be restored due to archive tampering", critical);
            // TODO: Send urgent notification to org admins
        }

        if (restored > 0) {
            log.warn("⚠️ ALERT: {} votes were tampered with but successfully restored", restored);
            // TODO: Send warning notification to org admins
        }


        // incidents.forEach(incident -> {
        //     emailService.sendAlert(
        //         getOrgAdmins(incident.getOrgId()),
        //         "Vote Tampering Detected",
        //         buildAlertEmail(incident)
        //     );
        // });
    }

    /**
     * Alert admins when config changes during voting.
     */
    public void notifyConfigChange(Election election, Candidate candidate, String newName) {
        log.warn("🚨 CONFIG CHANGE ALERT: Election {} - Candidate name changed to '{}'",
                election.getElectionId(), newName);

        // TODO: Send notification to org admins
        // Example:
        // emailService.sendAlert(
        //     getOrgAdmins(election.getOrgId()),
        //     "Election Config Changed During Voting",
        //     String.format("Candidate %s name changed to %s", candidate.getCandidateId(), newName)
        // );
    }
}