package com.brandedhustlers.electionbackend.voter;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import com.brandedhustlers.electionbackend.voter.mapper.VoterMapper;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voter.service.AttributeExtractor;
import com.brandedhustlers.electionbackend.voter.service.VoterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoterServiceTest {

    @Mock
    AppUserRepository appUserRepository;
    @Mock
    VoterMapper voterMapper;
    @Mock
    VoterRepository voterRepository;
    @Mock
    AuditEmitter auditEmitter;
    @Mock
    AttributeExtractor attributeExtractor;
    @Mock
    GetUser getUser;
    @Mock
    Util util;
    @InjectMocks
    VoterService voterService;

    private Organization org;
    private AppUser adminUser;
    private AppUser regularUser;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setOrganizationId(UUID.randomUUID());
        org.setOrganizationName("Test Org");

        adminUser = new AppUser();
        adminUser.setAppUserId(UUID.randomUUID());
        adminUser.setFirstName("Admin");
        adminUser.setUserRole(UserRole.ADMIN);
        UserOrganization adminUo = UserOrganization.builder()
                .id(UUID.randomUUID()).appUser(adminUser).organization(org)
                .roleInOrg("ADMIN").activeOrg(true).build();
        adminUser.setUserOrganizations(new HashSet<>(Set.of(adminUo)));

        regularUser = new AppUser();
        regularUser.setAppUserId(UUID.randomUUID());
        regularUser.setUserRole(UserRole.VOTER);
        UserOrganization regularUo = UserOrganization.builder()
                .id(UUID.randomUUID()).appUser(regularUser).organization(org)
                .roleInOrg("VOTER").activeOrg(true).build();
        regularUser.setUserOrganizations(new HashSet<>(Set.of(regularUo)));
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Nested
    class Create {

        // VoterDTO(String name, String phone, String email, String nationalId,
        // VoterStatus voterStatus, UUID voterId)
        private final CreateVoterCmd cmd = new CreateVoterCmd(
                "John Doe", "0241234567", "john@example.com", "GHA-199012345-1", null);

        @Test
        void createsVoterForAdmin() {
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
            when(util.authorizeUser(adminUser, UserRole.ADMIN)).thenReturn(true);
            when(attributeExtractor.extractAttributes(any(), any(), any(), any()))
                    .thenReturn(Map.of("age", 34, "citizen", true));

            // Service builds a new Voter internally — use any() so the stub matches
            // regardless of which instance ends up being saved and returned
            Voter saved = Voter.builder().build();
            when(voterRepository.save(any(Voter.class))).thenReturn(saved);

            VoterDTO dto = new VoterDTO("John Doe", "0241234567", "john@example.com", "GHA-199012345-1", null,
                    UUID.randomUUID());
            when(voterMapper.entityToDto(any(Voter.class))).thenReturn(dto);

            VoterDTO result = voterService.create(org.getOrganizationId(), cmd);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("John Doe");
            verify(voterRepository).save(any(Voter.class));
            verify(auditEmitter).emit(
                    any(), eq("voters.voter_created"), any(), eq(org.getOrganizationId()),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsForNonAdmin() {
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(regularUser);

            assertThatThrownBy(() -> voterService.create(org.getOrganizationId(), cmd))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("authority");
        }

        @Test
        void throwsWhenCallerHasNoOrg() {
            adminUser.setUserOrganizations(new HashSet<>());
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
            when(util.authorizeUser(adminUser, UserRole.ADMIN)).thenReturn(true);

            assertThatThrownBy(() -> voterService.create(org.getOrganizationId(), cmd))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("organization");
        }
    }

    // ─── getOrgVoters ─────────────────────────────────────────────────────────

    @Nested
    class GetOrgVoters {

        @Test
        void delegatesToRepoAndMapper() {
            UUID orgId = org.getOrganizationId();
            when(getUser.getCurrentAuthenticatedUserOrgId()).thenReturn(orgId);

            Voter v = Voter.builder().build();
            VoterDTO dto = new VoterDTO("Alice", null, null, null, null, UUID.randomUUID());

            when(voterRepository.findByOrgId(orgId)).thenReturn(List.of(v));
            when(voterMapper.entityToDto(v)).thenReturn(dto);

            Pageable pageable = PageRequest.of(0, 10);
            var page = voterService.getOrgVoters(pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).name()).isEqualTo("Alice");
        }
    }
}
