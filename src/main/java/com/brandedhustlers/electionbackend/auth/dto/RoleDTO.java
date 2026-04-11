package com.brandedhustlers.electionbackend.auth.dto;

import com.brandedhustlers.electionbackend.auth.enums.PermissionType;

import java.util.Set;
import java.util.UUID;

/**
 * Read model for a role.
 *
 * @param roleId      Internal role ID.
 * @param roleName    Display name.
 * @param orgId       null = system role (BH-seeded), non-null = org-scoped custom role.
 * @param system      Convenience flag: true when orgId is null.
 * @param permissions The permission types currently assigned to this role.
 */
public record RoleDTO(
        Long roleId,
        String roleName,
        UUID orgId,
        boolean system,
        Set<PermissionType> permissions
) {}
