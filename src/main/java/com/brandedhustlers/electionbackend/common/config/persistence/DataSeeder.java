package com.brandedhustlers.electionbackend.common.config.persistence;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.auth.repository.PermissionRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository userRepository;

    public DataSeeder(PermissionRepository permissionRepository,
                      RoleRepository roleRepository,
                      OrganizationRepository organizationRepository,
                      AppUserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Make it safe to re-run
        if (userRepository.count() > 0 && organizationRepository.count() > 0) return;

        // 1) Seed all Permission rows from the enum
        Map<PermissionType, Permission> perms = seedPermissions();

        // 2) Ensure Role rows exist from UserRole enum
        Map<UserRole, Role> roles = seedRoles();

        // 3) Attach permission sets to roles
        assignPermissionsToRoles(roles, perms);

        // 4) Seed Organizations
        Map<String, Organization> orgs = seedOrganizations();

        // 5) Seed Users (and attach roles + enum)
        seedUsers(roles, orgs);
    }

    private Map<PermissionType, Permission> seedPermissions() {
        Map<PermissionType, Permission> out = new EnumMap<>(PermissionType.class);
        for (PermissionType type : PermissionType.values()) {
            Permission existing = permissionRepository.findByPermissionType(type).orElse(null);
            if (existing == null) {
                Permission p = new Permission();
                p.setPermissionType(type);
                existing = permissionRepository.save(p);
            }
            out.put(type, existing);
        }
        return out;
    }

    private Map<UserRole, Role> seedRoles() {
        Map<UserRole, Role> out = new EnumMap<>(UserRole.class);
        for (UserRole ur : UserRole.values()) {
            Role existing = roleRepository.findByRoleName(ur.name()).orElse(null);
            if (existing == null) {
                Role r = new Role();
                r.setRoleName(ur.name());
                r = roleRepository.save(r);
                existing = r;
            }
            out.put(ur, existing);
        }
        return out;
    }

    // put this at class level (inside DataSeeder, but OUTSIDE any other method)
    private Set<Permission> permSet(Map<PermissionType, Permission> perms,
                                    PermissionType... types) {
        return Arrays.stream(types)
                .map(perms::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void assignPermissionsToRoles(Map<UserRole, Role> roles,
                                          Map<PermissionType, Permission> perms) {


        // Buckets (tweak as you like)
        Set<Permission> adminSet = permSet(perms,
                PermissionType.ADD_USERS, PermissionType.EDIT_USERS, PermissionType.DELETE_USERS, PermissionType.VIEW_USERS,
                PermissionType.ADD_ROLES, PermissionType.EDIT_ROLES, PermissionType.DELETE_ROLES, PermissionType.VIEW_ROLES,
                PermissionType.ADD_TAX, PermissionType.EDIT_TAX, PermissionType.DELETE_TAX, PermissionType.VIEW_TAX,
                PermissionType.ADD_REPORTS, PermissionType.EDIT_REPORTS, PermissionType.DELETE_REPORTS, PermissionType.VIEW_REPORTS,
                PermissionType.ADD_SETTINGS, PermissionType.EDIT_SETTINGS, PermissionType.DELETE_SETTINGS, PermissionType.VIEW_SETTINGS,
                PermissionType.EDIT_ELECTIONS
        );

        Set<Permission> auditorSet = permSet(perms,
                PermissionType.VIEW_USERS, PermissionType.VIEW_ROLES,
                PermissionType.VIEW_TAX, PermissionType.VIEW_REPORTS, PermissionType.VIEW_SETTINGS
        );

        Set<Permission> contributorSet = permSet(perms,
                PermissionType.VIEW_USERS, PermissionType.VIEW_ROLES, PermissionType.VIEW_TAX,
                PermissionType.ADD_REPORTS, PermissionType.EDIT_REPORTS, PermissionType.VIEW_REPORTS,
                PermissionType.EDIT_ELECTIONS
        );

        Set<Permission> observerSet = permSet(perms,
                PermissionType.VIEW_USERS, PermissionType.VIEW_ROLES,
                PermissionType.VIEW_TAX, PermissionType.VIEW_REPORTS, PermissionType.VIEW_SETTINGS
        );

        // SUPER_ADMIN gets everything
        Set<Permission> superAll = new HashSet<>(perms.values());

        upsertRolePermissions(roles.get(UserRole.SUPER_ADMIN), superAll);
        if (roles.containsKey(UserRole.ADMIN))       upsertRolePermissions(roles.get(UserRole.ADMIN), adminSet);
        if (roles.containsKey(UserRole.AUDITOR))     upsertRolePermissions(roles.get(UserRole.AUDITOR), auditorSet);
        if (roles.containsKey(UserRole.CONTRIBUTOR)) upsertRolePermissions(roles.get(UserRole.CONTRIBUTOR), contributorSet);
        if (roles.containsKey(UserRole.OBSERVER))    upsertRolePermissions(roles.get(UserRole.OBSERVER), observerSet);
    }

    private void upsertRolePermissions(Role role, Set<Permission> desired) {
        if (role == null) return;
        Set<Permission> current = role.getRolePermissions();
        if (current == null) current = new HashSet<>();
        if (!current.equals(desired)) {
            role.setRolePermissions(new HashSet<>(desired));
            roleRepository.save(role);
        }
    }

    private Map<String, Organization> seedOrganizations() {
        List<Organization> toEnsure = List.of(
                Organization.builder()
                        .organizationName("Electoral Commission")
                        .organizationDescription("National election management body")
                        .isEnabled(true)
                        .deleteYn(DeleteYn.N)
                        .build(),
                Organization.builder()
                        .organizationName("Open Civic Alliance")
                        .organizationDescription("Civil society monitors")
                        .isEnabled(true)
                        .deleteYn(DeleteYn.N)
                        .build(),
                Organization.builder()
                        .organizationName("Demo Party")
                        .organizationDescription("Sample political organization")
                        .isEnabled(true)
                        .deleteYn(DeleteYn.N)
                        .build()
        );

        Map<String, Organization> map = new HashMap<>();
        for (Organization org : toEnsure) {
            Organization o = organizationRepository
                    .findByName(org.getOrganizationName())
                    .orElseGet(() -> organizationRepository.save(org));
            map.put(o.getOrganizationName(), o);
        }
        return map;
    }

    private Set<Role> roleSet(Map<UserRole, Role> roles, UserRole... types) {
        return Arrays.stream(types)
                .map(roles::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void seedUsers(Map<UserRole, Role> roles, Map<String, Organization> orgs) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);


        List<AppUser> users = List.of(
                AppUser.builder()
                        .firstName("Elikem").lastName("Fenuku")
                        .username("kingmaker")
                        .appUserEmail("elikemfenuksu@gmail.com")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.SUPER_ADMIN)
                        .roles(new HashSet<>(roles.values()))
                        .organizationName("Electoralz Commission")
                        .appUserOrganization(orgs.get("Electoralz Commission"))
                        .build(),
                AppUser.builder()
                        .firstName("Ekow").lastName("Davis")
                        .username("davis")
                        .appUserEmail("reynoldsfenuku@gmail.com")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.ADMIN)
                        .roles(roleSet(roles, UserRole.ADMIN))
                        .organizationName("Electoralz Commission")
                        .appUserOrganization(orgs.get("Electoralz Commission"))
                        .build(),
                AppUser.builder()
                        .firstName("Nat").lastName("Auditor")
                        .username("auditor")
                        .appUserEmail("auditor@civic.local")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.AUDITOR)
                        .roles(roleSet(roles, UserRole.AUDITOR))
                        .organizationName("Open Civic Alliance")
                        .appUserOrganization(orgs.get("Open Civic Alliance"))
                        .build(),
                AppUser.builder()
                        .firstName("Joel").lastName("Contributor")
                        .username("contrib")
                        .appUserEmail("contrib@demoparty.local")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.CONTRIBUTOR)
                        .roles(roleSet(roles, UserRole.CONTRIBUTOR))
                        .organizationName("Demo Party")
                        .appUserOrganization(orgs.get("Demo Party"))
                        .build(),
                AppUser.builder()
                        .firstName("roman").lastName("Observer")
                        .username("reigns")
                        .appUserEmail("observer@civic.local")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.OBSERVER)
                        .roles(roleSet(roles, UserRole.OBSERVER))
                        .organizationName("Open Civic Alliance")
                        .appUserOrganization(orgs.get("Open Civic Alliance"))
                        .build()
        );

        for (AppUser u : users) {
            boolean exists = userRepository.findByUsernameOrEmail(
                    StringUtils.hasText(u.getAppUserEmail()) ? u.getAppUserEmail() : u.getUsername()
            ).isPresent();
            if (!exists) {
                userRepository.save(u);
            }
        }
    }
}

