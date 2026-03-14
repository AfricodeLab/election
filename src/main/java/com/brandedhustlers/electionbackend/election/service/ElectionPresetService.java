package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import com.brandedhustlers.electionbackend.election.domain.ElectionPreset;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionPresetRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionPresetDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElectionPresetRequest;
import com.brandedhustlers.electionbackend.election.repository.ElectionPresetRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElectionPresetService {

    private final ElectionPresetRepository electionPresetRepository;
    private final ElectionTypeRepository electionTypeRepository;
    private final GetUser getUser;
    private final Util util;

    /**
     * Create a new preset for the caller's organization.
     */
    public ElectionPresetDTO createPreset(CreateElectionPresetRequest request) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);

        if (caller.getActiveOrganization() == null) {
            throw new AccessDeniedException("You must belong to an organization to create presets");
        }

        ElectionTypeEntity type = electionTypeRepository.findByElectionTypeId(request.typeId())
                .orElseThrow(() -> new InvalidUpdate("Election type not found: " + request.typeId()));

        ElectionPreset preset = ElectionPreset.builder()
                .name(request.name())
                .description(request.description())
                .electionType(type)
                .organization(caller.getActiveOrganization())
                .defaultEligibilityPolicy(request.defaultEligibilityPolicy())
                .defaultExpectedVoterCount(request.defaultExpectedVoterCount())
                .defaultShouldEndAutomatically(request.defaultShouldEndAutomatically())
                .createdBy(caller)
                .deleteYn(DeleteYn.N)
                .build();

        ElectionPreset saved = electionPresetRepository.save(preset);
        log.info("Admin {} created preset '{}' (id={}) of type '{}'",
                caller.getAppUserId(), saved.getName(), saved.getPresetId(), type.getName());
        return toDTO(saved);
    }

    /**
     * Get all presets for the caller's organization, optionally filtered by typeId.
     */
    public List<ElectionPresetDTO> getPresetsForOrg(UUID typeId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        if (caller.getActiveOrganization() == null)
            return List.of();

        UUID orgId = caller.getActiveOrganization().getOrganizationId();

        List<ElectionPreset> presets = (typeId != null)
                ? electionPresetRepository.findByOrgIdAndTypeId(orgId, typeId)
                : electionPresetRepository.findByOrgId(orgId);

        return presets.stream().map(this::toDTO).toList();
    }

    /**
     * Update an existing preset. Only non-null fields in the request are applied.
     */
    public ElectionPresetDTO updatePreset(UUID presetId, UpdateElectionPresetRequest request) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);

        ElectionPreset preset = electionPresetRepository.findByPresetIdAndDeleteYn(presetId, DeleteYn.N)
                .orElseThrow(() -> new InvalidUpdate("Preset not found: " + presetId));

        Organization callerOrg = caller.getActiveOrganization();
        if (callerOrg == null) {
            throw new AccessDeniedException("No active organization. Please join or switch to an organization first.");
        }
        if (!preset.getOrganization().getOrganizationId().equals(callerOrg.getOrganizationId())) {
            throw new AccessDeniedException("You can only edit presets from your own organization");
        }

        if (request.name() != null) {
            preset.setName(request.name());
        }
        if (request.description() != null) {
            preset.setDescription(request.description());
        }
        if (request.typeId() != null) {
            ElectionTypeEntity type = electionTypeRepository.findByElectionTypeId(request.typeId())
                    .orElseThrow(() -> new InvalidUpdate("Election type not found: " + request.typeId()));
            preset.setElectionType(type);
        }
        if (request.defaultEligibilityPolicy() != null) {
            preset.setDefaultEligibilityPolicy(request.defaultEligibilityPolicy());
        }
        if (request.defaultExpectedVoterCount() != null) {
            preset.setDefaultExpectedVoterCount(request.defaultExpectedVoterCount());
        }
        if (request.defaultShouldEndAutomatically() != null) {
            preset.setDefaultShouldEndAutomatically(request.defaultShouldEndAutomatically());
        }

        ElectionPreset saved = electionPresetRepository.save(preset);
        log.info("Admin {} updated preset '{}' (id={})", caller.getAppUserId(), saved.getName(), presetId);
        return toDTO(saved);
    }

    /**
     * Soft-delete a preset.
     */
    public void deletePreset(UUID presetId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdmin(caller);

        ElectionPreset preset = electionPresetRepository.findByPresetIdAndDeleteYn(presetId, DeleteYn.N)
                .orElseThrow(() -> new InvalidUpdate("Preset not found: " + presetId));

        Organization callerOrg = caller.getActiveOrganization();
        if (callerOrg == null) {
            throw new AccessDeniedException("No active organization. Please join or switch to an organization first.");
        }
        if (!preset.getOrganization().getOrganizationId()
                .equals(callerOrg.getOrganizationId())) {
            throw new AccessDeniedException("You can only delete presets from your own organization");
        }

        preset.setDeleteYn(DeleteYn.Y);
        electionPresetRepository.save(preset);
        log.info("Admin {} deleted preset '{}' (id={})", caller.getAppUserId(), preset.getName(), presetId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private ElectionPresetDTO toDTO(ElectionPreset p) {
        ElectionTypeEntity t = p.getElectionType();
        ElectionTypeDTO typeDTO = new ElectionTypeDTO(
                t.getTypeId(),
                t.getName(),
                t.getDescription(),
                t.isSystem(),
                t.getOrganization() != null ? t.getOrganization().getOrganizationId() : null,
                t.getCreatedAt());

        return new ElectionPresetDTO(
                p.getPresetId(),
                p.getName(),
                p.getDescription(),
                typeDTO,
                p.getOrganization().getOrganizationId(),
                p.getDefaultEligibilityPolicy(),
                p.getDefaultExpectedVoterCount(),
                p.isDefaultShouldEndAutomatically(),
                p.getCreatedAt());
    }
}
