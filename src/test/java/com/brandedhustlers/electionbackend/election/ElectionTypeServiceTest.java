package com.brandedhustlers.electionbackend.election;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionTypeRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import com.brandedhustlers.electionbackend.election.service.ElectionTypeService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectionTypeServiceTest {

        @Mock
        ElectionTypeRepository electionTypeRepository;
        @Mock
        GetUser getUser;
        @Mock
        Util util;
        @InjectMocks
        ElectionTypeService service;

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
                regularUser.setFirstName("Voter");
                regularUser.setUserRole(UserRole.VOTER);
                UserOrganization regularUo = UserOrganization.builder()
                        .id(UUID.randomUUID()).appUser(regularUser).organization(org)
                        .roleInOrg("VOTER").activeOrg(true).build();
                regularUser.setUserOrganizations(new HashSet<>(Set.of(regularUo)));
        }

        // ─── createCustomType ─────────────────────────────────────────────────────

        @Nested
        class CreateCustomType {

                @Test
                void successForAdmin() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        when(electionTypeRepository.existsByNameAndOrganization("Board Election", org))
                                        .thenReturn(false);

                        ElectionTypeEntity saved = ElectionTypeEntity.builder()
                                        .typeId(UUID.randomUUID())
                                        .name("Board Election")
                                        .description("For board seats")
                                        .isSystem(false)
                                        .organization(org)
                                        .deleteYn(DeleteYn.N)
                                        .build();

                        when(electionTypeRepository.save(any(ElectionTypeEntity.class))).thenReturn(saved);

                        ElectionTypeDTO result = service
                                        .createCustomType(new CreateElectionTypeRequest("Board Election",
                                                        "For board seats"));

                        assertThat(result.name()).isEqualTo("Board Election");
                        assertThat(result.isSystem()).isFalse();
                        verify(electionTypeRepository).save(any(ElectionTypeEntity.class));
                }

                @Test
                void throwsAccessDeniedForNonAdmin() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(regularUser);
                        doThrow(new AccessDeniedException("Only admins can manage election types"))
                                        .when(util).requireAdmin(regularUser);

                        assertThatThrownBy(() -> service.createCustomType(new CreateElectionTypeRequest("X", null)))
                                        .isInstanceOf(AccessDeniedException.class)
                                        .hasMessageContaining("Only admins");
                }

                @Test
                void throwsAccessDeniedWhenNoOrg() {
                        adminUser.setUserOrganizations(new HashSet<>());
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        assertThatThrownBy(() -> service.createCustomType(new CreateElectionTypeRequest("X", null)))
                                        .isInstanceOf(AccessDeniedException.class)
                                        .hasMessageContaining("organization");
                }

                @Test
                void throwsConflictForDuplicateName() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        when(electionTypeRepository.existsByNameAndOrganization("Duplicate", org)).thenReturn(true);

                        assertThatThrownBy(() -> service
                                        .createCustomType(new CreateElectionTypeRequest("Duplicate", null)))
                                        .isInstanceOf(com.brandedhustlers.electionbackend.common.exceptions.ConflictAction.class)
                                        .hasMessageContaining("already exists");
                }
        }

        // ─── getVisibleTypes ──────────────────────────────────────────────────────

        @Nested
        class GetVisibleTypes {

                @Test
                void returnsOrgAndSystemTypesWhenUserHasOrg() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        ElectionTypeEntity systemType = ElectionTypeEntity.builder()
                                        .typeId(UUID.randomUUID()).name("PRESIDENTIAL").isSystem(true)
                                        .deleteYn(DeleteYn.N).build();
                        ElectionTypeEntity customType = ElectionTypeEntity.builder()
                                        .typeId(UUID.randomUUID()).name("Board Election").isSystem(false)
                                        .organization(org).deleteYn(DeleteYn.N).build();

                        when(electionTypeRepository.findVisibleToOrg(org.getOrganizationId()))
                                        .thenReturn(List.of(systemType, customType));

                        List<ElectionTypeDTO> result = service.getVisibleTypes();

                        assertThat(result).hasSize(2);
                        verify(electionTypeRepository).findVisibleToOrg(org.getOrganizationId());
                }

                @Test
                void returnsOnlySystemTypesWhenUserHasNoOrg() {
                        adminUser.setUserOrganizations(new HashSet<>());
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        ElectionTypeEntity systemType = ElectionTypeEntity.builder()
                                        .typeId(UUID.randomUUID()).name("PRESIDENTIAL").isSystem(true)
                                        .deleteYn(DeleteYn.N).build();

                        when(electionTypeRepository.findAllByIsSystemTrueAndDeleteYn(DeleteYn.N))
                                        .thenReturn(List.of(systemType));

                        List<ElectionTypeDTO> result = service.getVisibleTypes();

                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).isSystem()).isTrue();
                }
        }

        // ─── deleteType ───────────────────────────────────────────────────────────

        @Nested
        class DeleteType {

                @Test
                void softDeletesCustomType() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);

                        UUID typeId = UUID.randomUUID();
                        ElectionTypeEntity type = ElectionTypeEntity.builder()
                                        .typeId(typeId).name("Custom").isSystem(false)
                                        .organization(org).deleteYn(DeleteYn.N).build();

                        when(electionTypeRepository.findByElectionTypeId(typeId))
                                        .thenReturn(Optional.of(type));
                        when(electionTypeRepository.save(any())).thenReturn(type);

                        service.deleteType(typeId);

                        assertThat(type.getDeleteYn()).isEqualTo(DeleteYn.Y);
                        verify(electionTypeRepository).save(type);
                }

                @Test
                void throwsWhenTypeNotFound() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID typeId = UUID.randomUUID();
                        when(electionTypeRepository.findByElectionTypeId(typeId))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.deleteType(typeId))
                                        .isInstanceOf(InvalidUpdate.class)
                                        .hasMessageContaining("not found");
                }

                @Test
                void throwsWhenDeletingSystemType() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID typeId = UUID.randomUUID();

                        ElectionTypeEntity systemType = ElectionTypeEntity.builder()
                                        .typeId(typeId).name("PRESIDENTIAL").isSystem(true).organization(null)
                                        .deleteYn(DeleteYn.N).build();

                        when(electionTypeRepository.findByElectionTypeId(typeId))
                                        .thenReturn(Optional.of(systemType));

                        assertThatThrownBy(() -> service.deleteType(typeId))
                                        .isInstanceOf(InvalidUpdate.class)
                                        .hasMessageContaining("System");
                }

                @Test
                void throwsForCrossOrgDelete() {
                        when(getUser.getCurrentAuthenticatedUser()).thenReturn(adminUser);
                        UUID typeId = UUID.randomUUID();

                        Organization otherOrg = new Organization();
                        otherOrg.setOrganizationId(UUID.randomUUID());

                        ElectionTypeEntity type = ElectionTypeEntity.builder()
                                        .typeId(typeId).name("Foreign Type").isSystem(false)
                                        .organization(otherOrg).deleteYn(DeleteYn.N).build();

                        when(electionTypeRepository.findByElectionTypeId(typeId))
                                        .thenReturn(Optional.of(type));

                        assertThatThrownBy(() -> service.deleteType(typeId))
                                        .isInstanceOf(AccessDeniedException.class)
                                        .hasMessageContaining("own organization");
                }
        }
}
