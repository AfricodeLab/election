package com.brandedhustlers.electionbackend.results;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.mapper.CandidateMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import com.brandedhustlers.electionbackend.results.dto.TallyDto;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import com.brandedhustlers.electionbackend.results.service.ResultsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultsServiceTest {

    @Mock
    TallyRepository tallyRepository;
    @Mock
    AppUserRepository appUserRepository;
    @Mock
    CandidateRepository candidateRepository;
    @Mock
    CandidateMapper candidateMapper;
    @Mock
    ElectionRepository electionRepository;
    @Mock
    GetUser getUser;
    @Mock
    Util util;
    @InjectMocks
    ResultsService resultsService;

    private AppUser adminUser;
    private AppUser voterUser;
    private UUID electionId;

    @BeforeEach
    void setUp() {
        electionId = UUID.randomUUID();

        adminUser = new AppUser();
        adminUser.setAppUserId(UUID.randomUUID());
        adminUser.setUserRole(UserRole.ADMIN);
        adminUser.setRoles(Set.of());

        voterUser = new AppUser();
        voterUser.setAppUserId(UUID.randomUUID());
        voterUser.setUserRole(UserRole.VOTER);
        voterUser.setRoles(Set.of()); // no VIEW_RESULTS permission
    }

    // ─── get ─────────────────────────────────────────────────────────────────

    @Nested
    class Get {

        @Test
        void returnsPagedTalliesForAdmin() {
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

            UUID candidateId = UUID.randomUUID();
            TallyId tallyId = new TallyId(electionId, candidateId);
            Tally tally = new Tally(tallyId, 5L, Timestamp.from(Instant.now()));

            when(tallyRepository.findByIdElectionId(electionId)).thenReturn(List.of(tally));
            when(electionRepository.getElectionTitleById(electionId)).thenReturn("Presidential 2025");

            Candidate candidate = Candidate.builder().candidateId(candidateId).candidateName("Alice").build();
            when(candidateRepository.findAllById(anyCollection())).thenReturn(List.of(candidate));
            when(candidateMapper.entityToDTO(candidate)).thenReturn(mock(
                    com.brandedhustlers.electionbackend.election.dto.CandidateDTO.class));

            Pageable pageable = PageRequest.of(0, 10);
            Page<TallyDto> result = resultsService.get(electionId, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).electionTitle()).isEqualTo("Presidential 2025");
            assertThat(result.getContent().get(0).voteCount()).isEqualTo(5L);
        }

        @Test
        void returnsEmptyPageWhenNoTalliesExist() {
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
            when(tallyRepository.findByIdElectionId(electionId)).thenReturn(List.of());
            when(electionRepository.getElectionTitleById(electionId)).thenReturn("Empty Election");
            when(candidateRepository.findAllById(anyCollection())).thenReturn(List.of());

            Pageable pageable = PageRequest.of(0, 10);
            Page<TallyDto> result = resultsService.get(electionId, pageable);

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        void throwsAccessDeniedForVoterWithNoPermission() {
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(voterUser);
            doThrow(new AccessDeniedException("You lack sufficient privileges for this action"))
                    .when(util).requireAdminOrPermission(eq(voterUser), any());

            Pageable pageable = PageRequest.of(0, 10);

            assertThatThrownBy(() -> resultsService.get(electionId, pageable))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("privileges");
        }

        @Test
        void allowsVoterWithViewResultsPermission() {
            // Build a role containing VIEW_RESULTS permission
            com.brandedhustlers.electionbackend.auth.domain.Role role = new com.brandedhustlers.electionbackend.auth.domain.Role();

            Permission perm = new Permission();
            perm.setPermissionType(PermissionType.VIEW_RESULTS);
            role.setRolePermissions(Set.of(perm));

            voterUser.setRoles(Set.of(role));

            when(getUser.getCurrentAuthenticatedUser()).thenReturn(voterUser);
            when(tallyRepository.findByIdElectionId(electionId)).thenReturn(List.of());
            when(electionRepository.getElectionTitleById(electionId)).thenReturn("Open Election");
            when(candidateRepository.findAllById(anyCollection())).thenReturn(List.of());

            Pageable pageable = PageRequest.of(0, 10);
            Page<TallyDto> result = resultsService.get(electionId, pageable);

            assertThat(result).isNotNull();
        }
    }
}
