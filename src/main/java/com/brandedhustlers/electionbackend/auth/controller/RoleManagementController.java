package com.brandedhustlers.electionbackend.auth.controller;

import com.brandedhustlers.electionbackend.auth.dto.CreateRoleRequest;
import com.brandedhustlers.electionbackend.auth.dto.RoleDTO;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleService;

    // ── Role catalogue ────────────────────────────────────────────────────────

    /** List all roles visible to the caller (system roles + org's custom roles). */
    @GetMapping
    public ResponseEntity<List<RoleDTO>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles());
    }

    /**
     * List all available permission types.
     * This is a fixed, code-defined set — clients use it to populate
     * the permission picker when creating or editing a custom role.
     */
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionType>> listPermissions() {
        return ResponseEntity.ok(roleService.listPermissions());
    }

    /** Create a new org-scoped custom role. */
    @PostMapping
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(req));
    }

    /**
     * Replace the permission set on a custom role.
     * Passing an empty set clears all permissions.
     * System roles are immutable — this returns 400 for them.
     */
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<RoleDTO> updatePermissions(
            @PathVariable Long roleId,
            @RequestBody Set<PermissionType> permissions) {
        return ResponseEntity.ok(roleService.updateRolePermissions(roleId, permissions));
    }

    /** Delete a custom role. System roles are protected — this returns 400 for them. */
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    // ── User ↔ Role assignment ────────────────────────────────────────────────

    /** List all roles currently assigned to a user. */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<RoleDTO>> getUserRoles(@PathVariable UUID userId) {
        return ResponseEntity.ok(roleService.getUserRoles(userId));
    }

    /** Assign a role to a user. */
    @PostMapping("/users/{userId}/{roleId}")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @PathVariable Long roleId) {
        roleService.assignRoleToUser(userId, roleId);
        return ResponseEntity.ok().build();
    }

    /** Revoke a role from a user. */
    @DeleteMapping("/users/{userId}/{roleId}")
    public ResponseEntity<Void> revokeRole(
            @PathVariable UUID userId,
            @PathVariable Long roleId) {
        roleService.revokeRoleFromUser(userId, roleId);
        return ResponseEntity.noContent().build();
    }
}
