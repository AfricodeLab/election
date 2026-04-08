package com.brandedhustlers.electionbackend.election;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.ElectionPreset;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionPresetRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionPresetDTO;
import com.brandedhustlers.electionbackend.election.repository.ElectionPresetRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import com.brandedhustlers.electionbackend.election.service.ElectionPresetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectionPresetServiceTest {

        @Mock
        ElectionPresetRepository electionPresetRepository;
        @Mock
        ElectionTypeRepository electionTypeRepository;
        @Mock
        GetUser getUser;
        @Mock
        Util util;
        @InjectMocks
        ElectionPresetService service;

        private Organization org;
        private AppUser adminUser;
        private AppUser regularUser;
        private ElectionTypeEntity electionType;

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

                electionType = ElectionTypeEntity.builder()
                                .typeId(UUID.randomUUID())
                                .name("Board Election")
                                .isSystem(false)
                                .organization(org)
                                .deleteYn(DeleteYn.N)
                                .build();
        }

        // ─── createPreset ─────────────────────────────────────────────────────────

        @Nested
        class CreatePreset {

                @Test
                void successForAdmin() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        when(electionTypeRepository.findByElectionTypeId(electionType.getTypeId()))
                                        .thenReturn(Optional.of(electionType));

                        ElectionPreset saved = ElectionPreset.builder()
                                        .presetId(UUID.randomUUID())
                                        .name("Standard Board Setup")
                                        .electionType(electionType)
                                        .organization(org)
                                        .defaultExpectedVoterCount(200L)
                                        .defaultShouldEndAutomatically(true)
                                        .deleteYn(DeleteYn.N)
                                        .build();

                        when(electionPresetRepository.save(any(ElectionPreset.class))).thenReturn(saved);

                        CreateElectionPresetRequest req = new CreateElectionPresetRequest(
                                        "Standard Board Setup", null,
                                        electionType.getTypeId(), null, 200L, true);

                        ElectionPresetDTO result = service.createPreset(req);

                        assertThat(result.name()).isEqualTo("Standard Board Setup");
                        assertThat(result.defaultExpectedVoterCount()).isEqualTo(200L);
                        verify(electionPresetRepository).save(any(ElectionPreset.class));
                }

                @Test
                void throwsForNonAdmin() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(regularUser);
                        doThrow(new AccessDeniedException("Only admins can manage election types"))
                                        .when(util).requireAdminOrPermission(eq(regularUser), any(PermissionType.class));

                        assertThatThrownBy(() -> service.createPreset(
                                        new CreateElectionPresetRequest("X", null, UUID.randomUUID(), null, null,
                                                        true)))
                                        .isInstanceOf(AccessDeniedException.class);
                }

                @Test
                void throwsWhenTypeNotFound() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID unknownTypeId = UUID.randomUUID();
                        when(electionTypeRepository.findByElectionTypeId(unknownTypeId))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.createPreset(
                                        new CreateElectionPresetRequest("X", null, unknownTypeId, null, null, true)))
                                        .isInstanceOf(InvalidUpdate.class)
                                        .hasMessageContaining("not found");
                }

                @Test
                void throwsWhenCallerHasNoOrg() {
                        adminUser.setUserOrganizations(new HashSet<>());
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        assertThatThrownBy(() -> service.createPreset(
                                        new CreateElectionPresetRequest("X", null, UUID.randomUUID(), null, null,
                                                        true)))
                                        .isInstanceOf(AccessDeniedException.class)
                                        .hasMessageContaining("organization");
                }
        }

        // ─── getPresetsForOrg ─────────────────────────────────────────────────────

        @Nested
        class GetPresetsForOrg {

                @Test
                void returnsAllPresetsWhenNoTypeFilter() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        ElectionPreset preset = ElectionPreset.builder()
                                        .presetId(UUID.randomUUID())
                                        .name("Default")
                                        .electionType(electionType)
                                        .organization(org)
                                        .deleteYn(DeleteYn.N)
                                        .build();

                        when(electionPresetRepository.findByOrgId(org.getOrganizationId()))
                                        .thenReturn(List.of(preset));

                        List<ElectionPresetDTO> result = service.getPresetsForOrg(null);

                        assertThat(result).hasSize(1);
                        verify(electionPresetRepository).findByOrgId(org.getOrganizationId());
                }

                @Test
                void filtersByTypeIdWhenProvided() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID typeId = electionType.getTypeId();

                        when(electionPresetRepository.findByOrgIdAndTypeId(org.getOrganizationId(), typeId))
                                        .thenReturn(List.of());

                        service.getPresetsForOrg(typeId);

                        verify(electionPresetRepository).findByOrgIdAndTypeId(org.getOrganizationId(), typeId);
                }

                @Test
                void returnsEmptyListWhenNoOrg() {
                        adminUser.setUserOrganizations(new HashSet<>());
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        List<ElectionPresetDTO> result = service.getPresetsForOrg(null);
                        assertThat(result).isEmpty();
                        verifyNoInteractions(electionPresetRepository);
                }
        }

        // ─── deletePreset ─────────────────────────────────────────────────────────

        @Nested
        class DeletePreset {

                @Test
                void softDeletesPreset() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID presetId = UUID.randomUUID();

                        ElectionPreset preset = ElectionPreset.builder()
                                        .presetId(presetId)
                                        .name("Setup A")
                                        .electionType(electionType)
                                        .organization(org)
                                        .deleteYn(DeleteYn.N)
                                        .build();

                        when(electionPresetRepository.findByPresetIdAndDeleteYn(presetId, DeleteYn.N))
                                        .thenReturn(Optional.of(preset));
                        when(electionPresetRepository.save(any())).thenReturn(preset);

                        service.deletePreset(presetId);

                        assertThat(preset.getDeleteYn()).isEqualTo(DeleteYn.Y);
                        verify(electionPresetRepository).save(preset);
                }

                @Test
                void throwsForCrossOrgDelete() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID presetId = UUID.randomUUID();

                        Organization foreignOrg = new Organization();
                        foreignOrg.setOrganizationId(UUID.randomUUID());

                        ElectionPreset preset = ElectionPreset.builder()
                                        .presetId(presetId)
                                        .name("Foreign Preset")
                                        .electionType(electionType)
                                        .organization(foreignOrg)
                                        .deleteYn(DeleteYn.N)
                                        .build();

                        when(electionPresetRepository.findByPresetIdAndDeleteYn(presetId, DeleteYn.N))
                                        .thenReturn(Optional.of(preset));

                        doThrow(new AccessDeniedException("You cannot manage resources outside your organization."))
                                        .when(util).requireOrgMatch(any(), any());

                        assertThatThrownBy(() -> service.deletePreset(presetId))
                                        .isInstanceOf(AccessDeniedException.class)
                                        .hasMessageContaining("organization");
                }

                @Test
                void throwsWhenPresetNotFound() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID presetId = UUID.randomUUID();
                        when(electionPresetRepository.findByPresetIdAndDeleteYn(presetId, DeleteYn.N))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.deletePreset(presetId))
                                        .isInstanceOf(InvalidUpdate.class)
                                        .hasMessageContaining("not found");
                }
        }
}
