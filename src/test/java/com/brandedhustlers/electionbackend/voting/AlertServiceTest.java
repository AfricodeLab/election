package com.brandedhustlers.electionbackend.voting;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import com.brandedhustlers.electionbackend.voting.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlertService")
class AlertServiceTest {

    @Mock
    private MessagingService messagingService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private FeatureFlagService featureFlags;

    private AlertService alertService;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(messagingService, appUserRepository, featureFlags);
        orgId = UUID.randomUUID();
    }

    private TamperingLog incident(String type) {
        TamperingLog log = new TamperingLog();
        log.setOrgId(orgId);
        log.setIncidentType(type);
        return log;
    }

    private AppUser adminUser(UserRole role) {
        return AppUser.builder()
                .appUserId(UUID.randomUUID())
                .firstName("Admin")
                .appUserEmail("admin@org.com")
                .userRole(role)
                .build();
    }

    @Nested
    @DisplayName("notifyTamperingDetected — TAMPER_ALERTS gate")
    class TamperAlertsGate {

        @Test
        @DisplayName("Sends emails to all org admins when TAMPER_ALERTS is enabled")
        void sendsEmailsWhenEnabled() {
            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(true);
            when(appUserRepository.getUsersInOrg(orgId))
                    .thenReturn(List.of(
                            adminUser(UserRole.SUPER_ADMIN),
                            adminUser(UserRole.ORG_ADMIN)));

            alertService.notifyTamperingDetected(List.of(incident("ARCHIVE_TAMPERED")));

            verify(messagingService, times(2))
                    .sendTamperingAlert(any(), any(), eq("CRITICAL"), any());
        }

        @Test
        @DisplayName("Suppresses email when TAMPER_ALERTS is disabled")
        void suppressesEmailsWhenDisabled() {
            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(false);

            alertService.notifyTamperingDetected(List.of(incident("ARCHIVE_TAMPERED")));

            verifyNoInteractions(messagingService);
            verifyNoInteractions(appUserRepository);
        }

        @Test
        @DisplayName("Does nothing when incident list is empty")
        void doesNothingWhenEmpty() {
            alertService.notifyTamperingDetected(Collections.emptyList());

            verifyNoInteractions(messagingService, appUserRepository, featureFlags);
        }

        @Test
        @DisplayName("Does nothing when incident list is null")
        void doesNothingWhenNull() {
            alertService.notifyTamperingDetected(null);

            verifyNoInteractions(messagingService, appUserRepository, featureFlags);
        }

        @Test
        @DisplayName("Sends WARNING severity when no ARCHIVE_TAMPERED incidents")
        void sendsWarningSeverityForRestoredOnly() {
            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(true);
            when(appUserRepository.getUsersInOrg(orgId))
                    .thenReturn(List.of(adminUser(UserRole.ADMIN)));

            alertService.notifyTamperingDetected(List.of(incident("RESTORED")));

            verify(messagingService).sendTamperingAlert(any(), any(), eq("WARNING"), any());
        }

        @Test
        @DisplayName("No emails sent when no admin users found for org")
        void noEmailsWhenNoAdmins() {
            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(true);
            when(appUserRepository.getUsersInOrg(orgId)).thenReturn(Collections.emptyList());

            alertService.notifyTamperingDetected(List.of(incident("ARCHIVE_TAMPERED")));

            verifyNoInteractions(messagingService);
        }

        @Test
        @DisplayName("Only notifies SUPER_ADMIN, ADMIN, ORG_ADMIN — not OBSERVER or VOTER")
        void onlyNotifiesAdminRoles() {
            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(true);
            when(appUserRepository.getUsersInOrg(orgId))
                    .thenReturn(List.of(
                            adminUser(UserRole.SUPER_ADMIN),
                            adminUser(UserRole.OBSERVER),   // should be filtered out
                            adminUser(UserRole.VOTER)));    // should be filtered out

            alertService.notifyTamperingDetected(List.of(incident("ARCHIVE_TAMPERED")));

            // Only 1 email to SUPER_ADMIN
            verify(messagingService, times(1)).sendTamperingAlert(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("notifyConfigChange")
    class ConfigChange {

        @Test
        @DisplayName("Sends WARNING alert when candidate name changes during LIVE election")
        void sendsWarningForConfigChange() {
            Organization org = new Organization();
            org.setOrganizationId(orgId);

            Election election = Election.builder()
                    .electionId(UUID.randomUUID())
                    .electionTitle("Test Election")
                    .organization(org)
                    .build();

            Candidate candidate = Candidate.builder()
                    .candidateId(UUID.randomUUID())
                    .candidateName("Old Name")
                    .build();

            when(featureFlags.isEnabled(orgId, Feature.TAMPER_ALERTS)).thenReturn(true);
            when(appUserRepository.getUsersInOrg(orgId))
                    .thenReturn(List.of(adminUser(UserRole.ADMIN)));

            alertService.notifyConfigChange(election, candidate, "New Name");

            verify(messagingService).sendTamperingAlert(any(), any(), eq("WARNING"), contains("renamed"));
        }

        @Test
        @DisplayName("Skips alert when election has no organization")
        void skipsWhenNoOrg() {
            Election election = Election.builder()
                    .electionId(UUID.randomUUID())
                    .electionTitle("Test Election")
                    .organization(null)
                    .build();
            Candidate candidate = Candidate.builder().candidateId(UUID.randomUUID()).build();

            alertService.notifyConfigChange(election, candidate, "New Name");

            verifyNoInteractions(messagingService, appUserRepository);
        }
    }
}
