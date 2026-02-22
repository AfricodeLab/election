package com.brandedhustlers.electionbackend.election;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateCandidateCmd;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.mapper.CandidateMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.CandidateService;
import com.brandedhustlers.electionbackend.voting.service.AlertService;
import com.brandedhustlers.electionbackend.voting.service.ConfigSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    CandidateRepository candidateRepo;
    @Mock
    ElectionRepository electionRepo;
    @Mock
    ConfigSnapshotService snapshotService;
    @Mock
    AlertService alertService;
    @Mock
    CandidateMapper candidateMapper;
    @InjectMocks
    CandidateService service;

    private Organization org;
    private AppUser admin;
    private Candidate candidate;
    private Election election;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setOrganizationId(UUID.randomUUID());

        admin = new AppUser();
        admin.setAppUserId(UUID.randomUUID());
        admin.setFirstName("Admin");
        // Admin belongs to a DIFFERENT org so the equality check fails (service throws
        // if SAME org)
        Organization adminOrg = new Organization();
        adminOrg.setOrganizationId(UUID.randomUUID());
        admin.setAppUserOrganization(adminOrg);

        election = new Election();
        election.setElectionId(UUID.randomUUID());
        election.setElectionStatus(ElectionStatus.DRAFT);
        election.setOrganization(org);

        candidate = Candidate.builder()
                .candidateId(UUID.randomUUID())
                .candidateName("Alice")
                .candidatePosition(1)
                .election(election)
                .build();
    }

    // ─── not LIVE ─────────────────────────────────────────────────────────────

    @Nested
    class WhenElectionNotLive {

        @Test
        void updatesAllFieldsFreely() {
            when(candidateRepo.findById(candidate.getCandidateId())).thenReturn(Optional.of(candidate));
            when(candidateRepo.save(any())).thenReturn(candidate);
            when(candidateMapper.entityToDTO(any())).thenReturn(mock(CandidateDTO.class));

            UpdateCandidateCmd cmd = new UpdateCandidateCmd("Bob", 2);
            service.updateCandidate(candidate.getCandidateId(), cmd, admin);

            verify(candidateRepo).save(candidate);
            verifyNoInteractions(snapshotService, alertService);
        }
    }

    // ─── LIVE election ────────────────────────────────────────────────────────

    @Nested
    class WhenElectionLive {

        @BeforeEach
        void makeLive() {
            election.setElectionStatus(ElectionStatus.LIVE);
        }

        @Test
        void nameChangeCapturesSnapshotAndMarksReview() {
            when(candidateRepo.findById(candidate.getCandidateId())).thenReturn(Optional.of(candidate));
            when(candidateRepo.save(any())).thenReturn(candidate);
            when(electionRepo.save(any())).thenReturn(election);
            when(candidateMapper.entityToDTO(any())).thenReturn(mock(CandidateDTO.class));

            UpdateCandidateCmd cmd = new UpdateCandidateCmd("Bob", 1); // name changed
            service.updateCandidate(candidate.getCandidateId(), cmd, admin);

            verify(snapshotService).captureSnapshot(
                    eq(election.getElectionId()), eq("CHANGE_DETECTED"),
                    eq(admin.getAppUserId()), anyString());
            verify(alertService).notifyConfigChange(eq(election), eq(candidate), eq("Bob"));
            verify(electionRepo).save(election);
        }

        @Test
        void positionChangeIsAllowedWithoutSnapshot() {
            when(candidateRepo.findById(candidate.getCandidateId())).thenReturn(Optional.of(candidate));
            when(candidateRepo.save(any())).thenReturn(candidate);
            when(candidateMapper.entityToDTO(any())).thenReturn(mock(CandidateDTO.class));

            UpdateCandidateCmd cmd = new UpdateCandidateCmd("Alice", 3); // same name, different position
            service.updateCandidate(candidate.getCandidateId(), cmd, admin);

            verifyNoInteractions(snapshotService, alertService);
            verify(candidateRepo).save(candidate);
        }

        @Test
        void otherFieldChangesThrowIllegalStateException() {
            // same name AND same position = "other field change" branch
            when(candidateRepo.findById(candidate.getCandidateId())).thenReturn(Optional.of(candidate));

            UpdateCandidateCmd cmd = new UpdateCandidateCmd("Alice", 1);

            assertThatThrownBy(() -> service.updateCandidate(candidate.getCandidateId(), cmd, admin))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot modify");
        }
    }

    @Test
    void throwsWhenCandidateNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(candidateRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCandidate(unknownId, new UpdateCandidateCmd("X", 1), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
