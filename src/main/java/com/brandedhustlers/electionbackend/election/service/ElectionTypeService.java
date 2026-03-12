package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.ConflictAction;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionTypeRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeOptionDto;
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
    private final Util util;

    /**
     * Create a new custom election type for the caller's organization.
     * Only ADMIN or SUPER_ADMIN may create types.
     */
    public ElectionTypeDTO createCustomType(CreateElectionTypeRequest request) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);

        if (caller.getActiveOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create election types");
        }

        // Prevent duplicate name within same org
        if (electionTypeRepository.existsByNameAndOrganization(
                request.name(), caller.getActiveOrganization())) {
            throw new ConflictAction("An election type with this name already exists in your organization");
        }

        ElectionTypeEntity entity = ElectionTypeEntity.builder()
                .name(request.name())
                .description(request.description())
                .isSystem(false)
                .organization(caller.getActiveOrganization())
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

        if (caller.getActiveOrganization() == null) {
            // Super admins without an org: return system types only
            return electionTypeRepository
                    .findAllByIsSystemTrueAndDeleteYn(DeleteYn.N)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        return electionTypeRepository
                .findVisibleToOrg(caller.getActiveOrganization().getOrganizationId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Soft-delete a custom election type. System types cannot be deleted.
     */
    public void deleteType(UUID typeId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);

        ElectionTypeEntity type = electionTypeRepository.findByElectionTypeId(typeId)
                .orElseThrow(() -> new InvalidUpdate("Election type not found: " + typeId));

        if (type.isSystem()) {
            throw new InvalidUpdate("System election types cannot be deleted");
        }

        // Org scoping check
        if (type.getOrganization() == null ||
                !type.getOrganization().getOrganizationId()
                        .equals(caller.getActiveOrganization().getOrganizationId())) {
            throw new AccessDeniedException("You can only delete election types from your own organization");
        }

        type.setDeleteYn(DeleteYn.Y);
        electionTypeRepository.save(type);
        log.info("Admin {} soft-deleted election type '{}' (id={})",
                caller.getAppUserId(), type.getName(), typeId);
    }

    public List<ElectionTypeOptionDto> getElectionTypeOptions(){
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);
        return electionTypeRepository.getElectionTypeOption();
    }

    public List<ElectionTypeOptionDto> getElectionTypeOptionsByOrg(){
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);
        return electionTypeRepository.getElectionTypeOptionByOrg(getUser.getCurrentAuthenticatedUserOrgId());
    }



    private ElectionTypeDTO toDTO(ElectionTypeEntity e) {
        return new ElectionTypeDTO(
                e.getTypeId(),
                e.getName(),
                e.getDescription(),
                e.isSystem(),
                e.getOrganization() != null ? e.getOrganization().getOrganizationId() : null,
                e.getCreatedAt());
    }

}
