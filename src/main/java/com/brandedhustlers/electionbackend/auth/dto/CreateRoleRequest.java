package com.brandedhustlers.electionbackend.auth.dto;

import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request body for creating a new org-scoped custom role.
 *
 * @param name        Display name for the role (must be unique within the org).
 * @param permissions Initial set of permissions to grant. May be empty — permissions
 *                    can always be updated later via PUT /api/v1/roles/{id}/permissions.
 */
public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 50, message = "Role name must not exceed 50 characters")
        String name,

        Set<PermissionType> permissions
) {}
