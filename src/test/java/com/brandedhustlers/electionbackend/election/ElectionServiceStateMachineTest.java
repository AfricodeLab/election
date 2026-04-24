package com.brandedhustlers.electionbackend.election;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.mapper.CandidateMapper;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionPresetRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import com.brandedhustlers.electionbackend.election.service.ElectionService;
import com.brandedhustlers.electionbackend.election.service.EligibilityService;
import com.brandedhustlers.electionbackend.election.service.EligibilitySnapshotService;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voting.service.ConfigAnchorService;
import com.brandedhustlers.electionbackend.voting.service.ConfigSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ElectionService — state machine")
class ElectionServiceStateMachineTest {

    @Mock
    private ElectionRepository electionRepository;
    @Mock
    private ElectionMapper electionMapper;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private AuditEmitter auditEmitter;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private GetUser getUser;
    @Mock
    private ConfigSnapshotService configSnapshotService;
    @Mock
    private ConfigAnchorService configAnchorService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private VoterRepository voterRepository;
    @Mock
    private Util util;
    @Mock
    private ElectionTypeRepository electionTypeRepository;
    @Mock
    private ElectionPresetRepository electionPresetRepository;
    @Mock
    private EligibilityService eligibilityService;
    @Mock
    private JWTService jwtService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private EligibilitySnapshotService eligibilitySnapshotService;

    private ElectionService electionService;

    private UUID orgId;
    private UUID electionId;
    private Organization organization;
    private AppUser admin;
    private UserOrganization userOrganization;

    @BeforeEach
    void setUp() {
        electionService = new ElectionService(
                electionRepository, jwtService, appUserRepository,
                electionMapper, cloudinaryService, candidateRepository,
                auditEmitter, candidateMapper, getUser,
                configSnapshotService, configAnchorService,
                httpServletRequest, voterRepository, util,
                electionTypeRepository, electionPresetRepository,
                eligibilityService, eligibilitySnapshotService, rabbitTemplate);

        orgId = UUID.randomUUID();
        electionId = UUID.randomUUID();

        organization = new Organization();
        organization.setOrganizationId(orgId);

        admin = AppUser.builder()
                .appUserId(UUID.randomUUID())
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ORG_ADMIN)
                .build();

        userOrganization = new UserOrganization();
        userOrganization.setAppUser(admin);
        userOrganization.setId(UUID.randomUUID());
        userOrganization.setOrganization(organization);
        userOrganization.setRoleInOrg("ORG_ADMIN");
        userOrganization.setActiveOrg(true);
        userOrganization.setJoinedAt(Timestamp.from(Instant.now()));

        // Make the admin's active org match the election's org
        admin.getUserOrganizations().add(userOrganization);

        when(getUser.getCurrentAuthenticatedUser()).thenReturn(admin);
        doNothing().when(util).requireAdminOrPermission(any(), any());

        // Mapper returns a simple DTO
        when(electionMapper.entityToDto(any())).thenAnswer(inv -> {
            Election e = inv.getArgument(0);
            return new ElectionDTO(e.getElectionId(), e.getElectionTitle(),
                    null, null, e.getElectionStatus(), null, null, null, null, null, null, null, false, null, null);
        });
    }

    private Election electionWithStatus(ElectionStatus status) {
        Election e = Election.builder()
                .electionId(electionId)
                .electionTitle("Test Election")
                .electionStatus(status)
                .organization(organization)
                .build();
        when(electionRepository.findById(electionId)).thenReturn(Optional.of(e));
        when(electionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return e;
    }

    // ─── Valid transitions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("DRAFT → UPCOMING succeeds")
        void draftToUpcoming() {
            electionWithStatus(ElectionStatus.DRAFT);
            ElectionDTO result = electionService.changeElectionStatus(electionId, ElectionStatus.UPCOMING);
            assertEquals(ElectionStatus.UPCOMING, result.electionStatus());
        }

        @Test
        @DisplayName("DRAFT → LIVE succeeds")
        void draftToLive() {
            electionWithStatus(ElectionStatus.DRAFT);
            ElectionDTO result = electionService.changeElectionStatus(electionId, ElectionStatus.LIVE);
            assertEquals(ElectionStatus.LIVE, result.electionStatus());
        }

        @Test
        @DisplayName("UPCOMING → LIVE succeeds")
        void upcomingToLive() {
            electionWithStatus(ElectionStatus.UPCOMING);
            ElectionDTO result = electionService.changeElectionStatus(electionId, ElectionStatus.LIVE);
            assertEquals(ElectionStatus.LIVE, result.electionStatus());
        }

        @Test
        @DisplayName("UPCOMING → DRAFT succeeds")
        void upcomingToDraft() {
            electionWithStatus(ElectionStatus.UPCOMING);
            ElectionDTO result = electionService.changeElectionStatus(electionId, ElectionStatus.DRAFT);
            assertEquals(ElectionStatus.DRAFT, result.electionStatus());
        }

        @Test
        @DisplayName("LIVE → ENDED succeeds")
        void liveToEnded() {
            electionWithStatus(ElectionStatus.LIVE);
            ElectionDTO result = electionService.changeElectionStatus(electionId, ElectionStatus.ENDED);
            assertEquals(ElectionStatus.ENDED, result.electionStatus());
        }
    }

    // ─── Invalid transitions ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("DRAFT → RESULTS_RELEASED throws InvalidUpdate")
        void draftToResultsReleased() {
            electionWithStatus(ElectionStatus.DRAFT);
            assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.RESULTS_RELEASED));
        }

        @Test
        @DisplayName("UPCOMING → ENDED throws InvalidUpdate")
        void upcomingToEnded() {
            electionWithStatus(ElectionStatus.UPCOMING);
            assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.ENDED));
        }

        @Test
        @DisplayName("LIVE → DRAFT throws InvalidUpdate")
        void liveToDraft() {
            electionWithStatus(ElectionStatus.LIVE);
            assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.DRAFT));
        }

        @Test
        @DisplayName("LIVE → UPCOMING throws InvalidUpdate")
        void liveToUpcoming() {
            electionWithStatus(ElectionStatus.LIVE);
            assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.UPCOMING));
        }
    }

    // ─── Terminal state guards ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Terminal state guards")
    class TerminalStates {

        @Test
        @DisplayName("ENDED is terminal — throws InvalidUpdate before checking transition map")
        void endedIsTerminal() {
            electionWithStatus(ElectionStatus.ENDED);

            // Even a "valid" transition in the map should be blocked by the early guard
            InvalidUpdate ex = assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.RESULTS_RELEASED));

            assertTrue(ex.getMessage().contains("already ended"));
        }

        @Test
        @DisplayName("RESULTS_RELEASED is terminal — throws InvalidUpdate")
        void resultsReleasedIsTerminal() {
            electionWithStatus(ElectionStatus.RESULTS_RELEASED);

            InvalidUpdate ex = assertThrows(InvalidUpdate.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.ENDED));

            assertTrue(ex.getMessage().contains("Results have already been released"));
        }
    }

    // ─── Misc guard ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Election not found")
    class NotFound {

        @Test
        @DisplayName("Throws ElectionNotFound when election ID does not exist")
        void throwsWhenElectionMissing() {
            when(electionRepository.findById(electionId)).thenReturn(Optional.empty());

            assertThrows(ElectionNotFound.class,
                    () -> electionService.changeElectionStatus(electionId, ElectionStatus.LIVE));
        }
    }
}
