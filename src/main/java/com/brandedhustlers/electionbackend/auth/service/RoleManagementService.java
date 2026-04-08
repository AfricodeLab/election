package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.dto.CreateRoleRequest;
import com.brandedhustlers.electionbackend.auth.dto.RoleDTO;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.PermissionRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.exceptions.ConflictAction;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;
    private final GetUser getUser;
    private final Util util;

    // ── Roles ─────────────────────────────────────────────────────────────────

    /**
     * Returns all system roles plus any custom roles belonging to the caller's org.
     * Platform admins see system roles only (they have no single org context).
     */
    public List<RoleDTO> listRoles() {
        AppUser caller = getUser.getCurrentAuthenticatedUser();

        if (util.isPlatformAdmin(caller)) {
            return roleRepository.findByOrgIdIsNull().stream().map(this::toDTO).toList();
        }

        Organization org = caller.getActiveOrganization();
        if (org == null) {
            return roleRepository.findByOrgIdIsNull().stream().map(this::toDTO).toList();
        }

        return roleRepository.findSystemAndOrgRoles(org.getOrganizationId())
                .stream().map(this::toDTO).toList();
    }

    /** Returns the fixed set of all available permission types. */
    public List<PermissionType> listPermissions() {
        return Arrays.asList(PermissionType.values());
    }

    /**
     * Creates a new custom role scoped to the caller's organization.
     * Requires CREATE_ROLE permission (or admin).
     */
    @Transactional
    public RoleDTO createRole(CreateRoleRequest req) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.CREATE_ROLE);

        Organization org = caller.getActiveOrganization();
        if (org == null) {
            throw new AccessDeniedException("You must belong to an organization to create roles");
        }

        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank");
        }

        if (roleRepository.existsByRoleNameAndOrgId(req.name(), org.getOrganizationId())) {
            throw new ConflictAction("A role named '" + req.name() + "' already exists in your organization");
        }

        Role role = new Role();
        role.setRoleName(req.name());
        role.setOrgId(org.getOrganizationId());

        if (req.permissions() != null && !req.permissions().isEmpty()) {
            role.setRolePermissions(resolvePermissions(req.permissions()));
        }

        Role saved = roleRepository.save(role);
        log.info("Org {} created custom role '{}' (id={})", org.getOrganizationId(), saved.getRoleName(), saved.getRoleId());
        return toDTO(saved);
    }

    /**
     * Replaces the permission set on an org-scoped role.
     * Requires UPDATE_ROLE permission (or admin). System roles cannot be modified.
     */
    @Transactional
    public RoleDTO updateRolePermissions(Long roleId, Set<PermissionType> permissions) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.UPDATE_ROLE);

        Role role = findRoleOrThrow(roleId);

        if (role.getOrgId() == null) {
            throw new IllegalStateException("System roles cannot be modified");
        }

        util.requireOrgMatch(caller, role.getOrgId());

        role.setRolePermissions(resolvePermissions(permissions));
        Role saved = roleRepository.save(role);
        log.info("Updated permissions for role '{}' (id={})", saved.getRoleName(), roleId);
        return toDTO(saved);
    }

    /**
     * Deletes an org-scoped custom role.
     * Requires DELETE_ROLE permission (or admin). System roles cannot be deleted.
     */
    @Transactional
    public void deleteRole(Long roleId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.DELETE_ROLE);

        Role role = findRoleOrThrow(roleId);

        if (role.getOrgId() == null) {
            throw new IllegalStateException("System roles cannot be deleted");
        }

        util.requireOrgMatch(caller, role.getOrgId());

        roleRepository.delete(role);
        log.info("Deleted role '{}' (id={})", role.getRoleName(), roleId);
    }

    // ── User ↔ Role assignment ────────────────────────────────────────────────

    /**
     * Returns all roles currently assigned to a user.
     * Requires VIEW_ROLES permission (or admin).
     */
    public List<RoleDTO> getUserRoles(UUID userId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.VIEW_ROLES);

        AppUser user = findUserOrThrow(userId);
        return user.getRoles().stream().map(this::toDTO).toList();
    }

    /**
     * Assigns a role to a user.
     * Requires ASSIGN_ROLE permission (or admin).
     * For org-scoped roles, both the assigning admin and the target user must be in the same org.
     */
    @Transactional
    public void assignRoleToUser(UUID userId, Long roleId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.ASSIGN_ROLE);

        AppUser user = findUserOrThrow(userId);
        Role role = findRoleOrThrow(roleId);

        // Org-scoped roles may only be assigned within the owning org
        if (role.getOrgId() != null) {
            util.requireOrgMatch(caller, role.getOrgId());
        }

        user.getRoles().add(role);
        appUserRepository.save(user);
        log.info("Assigned role '{}' to user {}", role.getRoleName(), userId);
    }

    /**
     * Revokes a role from a user.
     * Requires ASSIGN_ROLE permission (or admin).
     */
    @Transactional
    public void revokeRoleFromUser(UUID userId, Long roleId) {
        AppUser caller = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(caller, PermissionType.ASSIGN_ROLE);

        AppUser user = findUserOrThrow(userId);
        boolean removed = user.getRoles().removeIf(r -> r.getRoleId().equals(roleId));

        if (removed) {
            appUserRepository.save(user);
            log.info("Revoked role {} from user {}", roleId, userId);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Role findRoleOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
    }

    private AppUser findUserOrThrow(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found: " + userId));
    }

    private Set<Permission> resolvePermissions(Set<PermissionType> types) {
        return types.stream()
                .map(t -> permissionRepository.findByPermissionType(t)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown permission: " + t)))
                .collect(Collectors.toSet());
    }

    private RoleDTO toDTO(Role role) {
        Set<PermissionType> permissions = role.getRolePermissions().stream()
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());
        return new RoleDTO(
                role.getRoleId(),
                role.getRoleName(),
                role.getOrgId(),
                role.getOrgId() == null,
                permissions
        );
    }
}
