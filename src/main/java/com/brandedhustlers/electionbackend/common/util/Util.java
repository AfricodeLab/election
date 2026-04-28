package com.brandedhustlers.electionbackend.common.util;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class Util {
    private final AppUserRepository appUserRepository;

    public Util(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    // ── Role helpers ─────────────────────────────────────────────────────────

    public boolean authorizeUser(AppUser user, UserRole userType) {
        return user.getUserRole().equals(userType);
    }

    /**
     * Returns true for Branded Hustlers platform staff (ADMIN, SUPER_ADMIN).
     * Platform admins cross org boundaries freely and bypass all permission checks.
     */
    public boolean isPlatformAdmin(AppUser user) {
        return user.getUserRole() == UserRole.ADMIN
                || user.getUserRole() == UserRole.SUPER_ADMIN;
    }

    /**
     * Passes if the actor is a platform admin or an ORG_ADMIN.
     * Use this for operations that any admin level can perform without needing
     * a specific delegated permission.
     */
    public void requireAdmin(AppUser user) {
        if (!isPlatformAdmin(user) && !authorizeUser(user, UserRole.ORG_ADMIN)) {
            throw new AccessDeniedException("Only admins can perform this action");
        }
    }

    // ── Permission helpers ───────────────────────────────────────────────────

    /**
     * Checks whether the user holds at least one of the given permissions,
     * considering only roles that are system-level (orgId = null) or scoped
     * to the user's currently active organization.
     */
    public boolean authorityCheck(UUID appUserId, PermissionType allowedPermission) {
        AppUser user = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        UUID activeOrgId = Optional.ofNullable(user.getActiveOrganization())
                .map(Organization::getOrganizationId)
                .orElse(null);

        Set<PermissionType> userPermissions = user.getRoles()
                .stream()
                // system roles (orgId null) apply everywhere;
                // custom roles apply only when the user is in that org
                .filter(role -> role.getOrgId() == null
                        || role.getOrgId().equals(activeOrgId))
                .flatMap(role -> role.getRolePermissions().stream())
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());

        return userPermissions.contains(allowedPermission);
    }

    /**
     * Enforces that the caller has at least one of the supplied permissions,
     * or is an admin (SUPER_ADMIN / ADMIN / ORG_ADMIN) who bypasses the check.
     *
     * <p>Accepts varargs so callers can express "permission A or permission B"
     * without building a collection at the call site.</p>
     */
    public void requireAdminOrPermission(AppUser user, PermissionType... permissions) {
        // Platform staff and org owners always pass
        if (isPlatformAdmin(user) || authorizeUser(user, UserRole.ORG_ADMIN)) {
            return;
        }

        boolean hasAnyPermission = Arrays.stream(permissions)
                .anyMatch(p -> authorityCheck(user.getAppUserId(), p));

        if (!hasAnyPermission) {
            throw new AccessDeniedException("You lack sufficient privileges for this action");
        }
    }

    // ── Org-scope guard ──────────────────────────────────────────────────────

    /**
     * Enforces that the actor may only operate on resources owned by their active org.
     * Platform admins (ADMIN, SUPER_ADMIN) bypass this check — they cross org
     * boundaries freely. ORG_ADMIN must have an active org that matches resourceOrgId.
     */
    public void requireOrgMatch(AppUser actor, UUID resourceOrgId) {
        if (isPlatformAdmin(actor)) return;
        Organization actorOrg = actor.getActiveOrganization();
        if (actorOrg == null || !actorOrg.getOrganizationId().equals(resourceOrgId)) {
            throw new AccessDeniedException("You cannot manage resources outside your organization.");
        }
    }
}
