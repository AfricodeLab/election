package com.brandedhustlers.electionbackend.common.config.persistence;

import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.PermissionRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs on every application startup (before DataSeeder) to keep the
 * {@code auth.permissions} table and system-role permission assignments
 * in sync with the current {@link PermissionType} enum.
 *
 * <p>This is intentionally not guarded by a "already seeded" check —
 * it is idempotent and handles enum additions, removals, and re-assignments
 * automatically without a new migration for every permission change.</p>
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class PermissionSyncer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("PermissionSyncer: syncing permissions and system role assignments...");

        Map<PermissionType, Permission> perms = syncPermissions();
        Map<UserRole, Role> roles = syncSystemRoles();
        assignPermissionsToRoles(roles, perms);

        log.info("PermissionSyncer: done. {} permissions active.", perms.size());
    }

    // ── Step 1: Ensure every enum value has a DB row ─────────────────────────

    private Map<PermissionType, Permission> syncPermissions() {
        Set<PermissionType> currentEnumValues = EnumSet.allOf(PermissionType.class);

        // Remove DB rows for enum values that no longer exist
        permissionRepository.findAll().forEach(p -> {
            if (!currentEnumValues.contains(p.getPermissionType())) {
                log.info("PermissionSyncer: removing obsolete permission '{}'", p.getPermissionType());
                permissionRepository.delete(p);
            }
        });

        // Ensure every current enum value has a DB row
        Map<PermissionType, Permission> out = new EnumMap<>(PermissionType.class);
        for (PermissionType type : currentEnumValues) {
            Permission p = permissionRepository.findByPermissionType(type)
                    .orElseGet(() -> {
                        log.info("PermissionSyncer: seeding new permission '{}'", type);
                        Permission np = new Permission();
                        np.setPermissionType(type);
                        return permissionRepository.save(np);
                    });
            out.put(type, p);
        }
        return out;
    }

    // ── Step 2: Ensure system Role rows exist for every UserRole ─────────────

    private Map<UserRole, Role> syncSystemRoles() {
        Map<UserRole, Role> out = new EnumMap<>(UserRole.class);
        for (UserRole ur : UserRole.values()) {
            Role role = roleRepository.findByRoleName(ur.name())
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setRoleName(ur.name());
                        // System roles have no org — orgId stays null
                        return roleRepository.save(r);
                    });
            out.put(ur, role);
        }
        return out;
    }

    // ── Step 3: Assign permissions to system roles ────────────────────────────

    private void assignPermissionsToRoles(Map<UserRole, Role> roles,
                                          Map<PermissionType, Permission> perms) {

        // SUPER_ADMIN — all permissions (platform god)
        upsert(roles.get(UserRole.SUPER_ADMIN), all(perms));

        // ADMIN — all permissions (BH support/ops, cross-org)
        upsert(roles.get(UserRole.ADMIN), all(perms));

        // ORG_ADMIN — all permissions (client owns their org; org-scope enforced separately)
        upsert(roles.get(UserRole.ORG_ADMIN), all(perms));

        // AUDITOR — read-only view permissions + audit + results
        upsert(roles.get(UserRole.AUDITOR), pick(perms,
                PermissionType.VIEW_ELECTIONS,
                PermissionType.VIEW_VOTERS,
                PermissionType.VIEW_RESULTS,
                PermissionType.VIEW_AUDIT_LOG,
                PermissionType.VIEW_USERS,
                PermissionType.VIEW_ROLES
        ));

        // CONTRIBUTOR — can help manage elections and voters but cannot publish/end
        upsert(roles.get(UserRole.CONTRIBUTOR), pick(perms,
                PermissionType.CREATE_ELECTION,
                PermissionType.UPDATE_ELECTION,
                PermissionType.VIEW_ELECTIONS,
                PermissionType.CREATE_VOTER,
                PermissionType.UPDATE_VOTER,
                PermissionType.IMPORT_VOTERS,
                PermissionType.ASSIGN_VOTERS,
                PermissionType.VIEW_VOTERS,
                PermissionType.MANAGE_CANDIDATES,
                PermissionType.CHECK_ELIGIBILITY,
                PermissionType.VIEW_RESULTS,
                PermissionType.VIEW_USERS
        ));

        // OBSERVER — read-only results and election status
        upsert(roles.get(UserRole.OBSERVER), pick(perms,
                PermissionType.VIEW_ELECTIONS,
                PermissionType.VIEW_RESULTS
        ));

        // VOTER — no internal permissions (access controlled by JWT role only)
        upsert(roles.get(UserRole.VOTER), Set.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<Permission> all(Map<PermissionType, Permission> perms) {
        return new HashSet<>(perms.values());
    }

    private Set<Permission> pick(Map<PermissionType, Permission> perms,
                                 PermissionType... types) {
        return Arrays.stream(types)
                .map(perms::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void upsert(Role role, Set<Permission> desired) {
        if (role == null) return;
        if (!new HashSet<>(role.getRolePermissions()).equals(desired)) {
            role.setRolePermissions(new HashSet<>(desired));
            roleRepository.save(role);
        }
    }
}
