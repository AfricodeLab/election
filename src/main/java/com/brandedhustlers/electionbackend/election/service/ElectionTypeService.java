package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionTypeRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionTypeService {

    private final ElectionTypeRepository electionTypeRepository;
    private final GetUser getUser;

    /**
     * Create a new custom election type for the caller's organization.
     * Only ADMIN or SUPER_ADMIN may create types.
     */
    public ElectionTypeDTO createCustomType(CreateElectionTypeRequest request) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        requireAdmin(caller);

        if (caller.getAppUserOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create election types");
        }

        // Prevent duplicate name within same org
        if (electionTypeRepository.existsByNameAndOrganization(
                request.name(), caller.getAppUserOrganization())) {
            throw new InvalidUpdate("An election type with this name already exists in your organization");
        }

        ElectionTypeEntity entity = ElectionTypeEntity.builder()
                .name(request.name())
                .description(request.description())
                .isSystem(false)
                .organization(caller.getAppUserOrganization())
                .createdBy(caller)
                .deleteYn(DeleteYn.N)
                .build();

        ElectionTypeEntity saved = electionTypeRepository.save(entity);
        log.info("Admin {} created custom election type '{}' (id={})",
                caller.getAppUserId(), saved.getName(), saved.getTypeId());
        return toDTO(saved);
    }

    /**
     * Get all election types accessible to the caller's organization
     * (system types + org-owned types).
     */
    public List<ElectionTypeDTO> getVisibleTypes() {
        AppUser caller = getUser.getCurrentAuthenticatedUser();

        if (caller.getAppUserOrganization() == null) {
            // Super admins without an org: return system types only
            return electionTypeRepository
                    .findAllByIsSystemTrueAndDeleteYn(DeleteYn.N)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        return electionTypeRepository
                .findVisibleToOrg(caller.getAppUserOrganization().getOrganizationId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Soft-delete a custom election type. System types cannot be deleted.
     */
    public void deleteType(UUID typeId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        requireAdmin(caller);

        ElectionTypeEntity type = electionTypeRepository.findByTypeIdAndDeleteYn(typeId, DeleteYn.N)
                .orElseThrow(() -> new InvalidUpdate("Election type not found: " + typeId));

        if (type.isSystem()) {
            throw new InvalidUpdate("System election types cannot be deleted");
        }

        // Org scoping check
        if (type.getOrganization() == null ||
                !type.getOrganization().getOrganizationId()
                        .equals(caller.getAppUserOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You can only delete election types from your own organization");
        }

        type.setDeleteYn(DeleteYn.Y);
        electionTypeRepository.save(type);
        log.info("Admin {} soft-deleted election type '{}' (id={})",
                caller.getAppUserId(), type.getName(), typeId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private ElectionTypeDTO toDTO(ElectionTypeEntity e) {
        return new ElectionTypeDTO(
                e.getTypeId(),
                e.getName(),
                e.getDescription(),
                e.isSystem(),
                e.getOrganization() != null ? e.getOrganization().getOrganizationId() : null,
                e.getCreatedAt());
    }

    private void requireAdmin(AppUser user) {
        if (!user.getUserRole().equals(UserRole.ADMIN)
                && !user.getUserRole().equals(UserRole.SUPER_ADMIN)) {
            throw new AccessDeniedException("Only admins can manage election types");
        }
    }
}
