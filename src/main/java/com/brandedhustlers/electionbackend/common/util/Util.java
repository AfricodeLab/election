package com.brandedhustlers.electionbackend.common.util;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class Util {
    private final AppUserRepository appUserRepository;

    public Util(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public void requireAdmin(AppUser user) {
        if (!user.getUserRole().equals(UserRole.ADMIN)
                && !user.getUserRole().equals(UserRole.SUPER_ADMIN)
                && !user.getUserRole().equals(UserRole.ORG_ADMIN)
        ) {
            throw new AccessDeniedException("Only admins can manage election types");
        }
    }

    public boolean authorizeUser(AppUser user, UserRole userType) {
        return user.getUserRole().equals(userType);
    }

    public boolean authorityCheck(UUID appUserId, PermissionType allowedPermission) {
        AppUser updatingUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        Set<PermissionType> userPermissions = updatingUser.getRoles()
                .stream()
                .flatMap(role -> role.getRolePermissions().stream())
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());

        return userPermissions.contains(allowedPermission);
    }

    public void requireAdminOrPermission(AppUser user, PermissionType permission) {
        boolean hasRole = authorizeUser(user, UserRole.ADMIN) || authorizeUser(user, UserRole.SUPER_ADMIN) || authorizeUser(user, UserRole.ORG_ADMIN);
        boolean hasPermission = authorityCheck(user.getAppUserId(), permission);

        if (!hasRole && !hasPermission)
            throw new AccessDeniedException("You lack sufficient privileges for this action");
    }
}
