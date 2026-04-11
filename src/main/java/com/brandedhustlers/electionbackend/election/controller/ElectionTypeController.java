package com.brandedhustlers.electionbackend.election.controller;

import com.brandedhustlers.electionbackend.election.dto.CreateElectionPresetRequest;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionTypeRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionPresetDTO;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElectionPresetRequest;
import com.brandedhustlers.electionbackend.election.service.ElectionPresetService;
import com.brandedhustlers.electionbackend.election.service.ElectionTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing dynamic election types and reusable presets.
 * <p>
 * Base path: {@code /api/v1/election-types}
 * <ul>
 * <li>Types: POST / GET / DELETE endpoints</li>
 * <li>Presets: POST / GET / DELETE under {@code /presets}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/election-types")
@RequiredArgsConstructor
public class ElectionTypeController {

    private final ElectionTypeService electionTypeService;
    private final ElectionPresetService electionPresetService;

    // ═══════════════════ Election Types ═══════════════════════════════════════

    /**
     * Create a new custom election type for the caller's organization.
     *
     * @param request name + optional description
     * @return the created {@link ElectionTypeDTO}
     */
    @PostMapping
    public ResponseEntity<ElectionTypeDTO> createType(
            @Valid @RequestBody CreateElectionTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(electionTypeService.createCustomType(request));
    }

    /**
     * List all election types visible to the caller's organization.
     * Returns system types plus the org's own custom types.
     */
    @GetMapping
    public ResponseEntity<List<ElectionTypeDTO>> listTypes() {
        return ResponseEntity.ok(electionTypeService.getVisibleTypes());
    }

    /**
     * Soft-delete a custom election type.
     * System-level types cannot be deleted.
     *
     * @param typeId UUID of the type to delete
     */
    @DeleteMapping("/{typeId}")
    public ResponseEntity<Void> deleteType(@PathVariable UUID typeId) {
        electionTypeService.deleteType(typeId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════ Election Presets ═════════════════════════════════════

    /**
     * Create a reusable election preset for the caller's organization.
     *
     * @param request preset name, linked typeId, optional default policy/counts
     * @return the created {@link ElectionPresetDTO}
     */
    @PostMapping("/presets")
    public ResponseEntity<ElectionPresetDTO> createPreset(
            @Valid @RequestBody CreateElectionPresetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(electionPresetService.createPreset(request));
    }

    /**
     * List all presets for the caller's organization.
     *
     * @param typeId optional filter by election type UUID
     */
    @GetMapping("/presets")
    public ResponseEntity<List<ElectionPresetDTO>> listPresets(
            @RequestParam(required = false) UUID typeId) {
        return ResponseEntity.ok(electionPresetService.getPresetsForOrg(typeId));
    }

    /**
     * Update an existing election preset.
     * Only non-null fields in the request body are applied.
     *
     * @param presetId UUID of the preset to update
     * @param request  fields to update
     * @return the updated {@link ElectionPresetDTO}
     */
    @PutMapping("/presets/{presetId}")
    public ResponseEntity<ElectionPresetDTO> updatePreset(
            @PathVariable UUID presetId,
            @Valid @RequestBody UpdateElectionPresetRequest request) {
        return ResponseEntity.ok(electionPresetService.updatePreset(presetId, request));
    }

    /**
     * Soft-delete an election preset.
     *
     * @param presetId UUID of the preset to delete
     */
    @DeleteMapping("/presets/{presetId}")
    public ResponseEntity<Void> deletePreset(@PathVariable UUID presetId) {
        electionPresetService.deletePreset(presetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/type")
    public ResponseEntity<?> getElectionTypeOptions() {
        return new ResponseEntity<>(electionTypeService.getElectionTypeOptions(), HttpStatus.OK);
    }

    @GetMapping("/type/org")
    public ResponseEntity<?> getElectionTypeOptionForOrg() {
        return new ResponseEntity<>(electionTypeService.getElectionTypeOptionsByOrg(), HttpStatus.OK);
    }
}
