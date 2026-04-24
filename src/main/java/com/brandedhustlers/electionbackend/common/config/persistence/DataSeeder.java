package com.brandedhustlers.electionbackend.common.config.persistence;

import com.brandedhustlers.electionbackend.auth.domain.*;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.repository.*;
import com.brandedhustlers.electionbackend.common.flags.Tier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Seeds the initial organizations and users on first startup.
 * Runs after {@link PermissionSyncer} ({@code @Order(0)}) so that
 * system roles and permissions are guaranteed to exist when users are created.
 *
 * <p>Guarded by a count check — will not re-run if any users or organizations
 * already exist in the database.</p>
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final PasswordEncoder encoder;

    public DataSeeder(RoleRepository roleRepository,
                      OrganizationRepository organizationRepository,
                      PasswordEncoder encoder,
                      AppUserRepository userRepository,
                      UserOrganizationRepository userOrganizationRepository) {
        this.encoder = encoder;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // PermissionSyncer (@Order 0) has already seeded permissions and system roles.
        // This seeder only creates organizations and users on first run.
        if (userRepository.count() > 0 && organizationRepository.count() > 0) {
            return;
        }

        Map<UserRole, Role> roles = loadSystemRoles();
        Map<String, Organization> orgs = seedOrganizations();
        seedUsers(roles, orgs);
    }

    // ── Load system roles created by PermissionSyncer ────────────────────────

    private Map<UserRole, Role> loadSystemRoles() {
        Map<UserRole, Role> out = new EnumMap<>(UserRole.class);
        for (UserRole ur : UserRole.values()) {
            roleRepository.findByRoleName(ur.name()).ifPresent(r -> out.put(ur, r));
        }
        return out;
    }

    // ── Organizations ─────────────────────────────────────────────────────────

    private Map<String, Organization> seedOrganizations() {
        List<Organization> toEnsure = List.of(
                // ENTERPRISE tier — flagship demo: all features unlocked
                Organization.builder()
                        .organizationName("Electoral Commission")
                        .organizationDescription("National election management body")
                        .isEnabled(true)
                        .tier(Tier.ENTERPRISE)
                        .deleteYn(DeleteYn.N)
                        .build(),

                // COMPLIANCE tier — audit trail + tamper alerts, no real-time push
                Organization.builder()
                        .organizationName("Open Civic Alliance")
                        .organizationDescription("Civil society monitors")
                        .isEnabled(true)
                        .tier(Tier.COMPLIANCE)
                        .deleteYn(DeleteYn.N)
                        .build(),

                // INTEGRITY tier — blockchain anchoring only
                Organization.builder()
                        .organizationName("Demo Party")
                        .organizationDescription("Sample political organization")
                        .isEnabled(true)
                        .tier(Tier.INTEGRITY)
                        .deleteYn(DeleteYn.N)
                        .build());

        Map<String, Organization> map = new HashMap<>();
        for (Organization org : toEnsure) {
            Organization o = organizationRepository
                    .findByName(org.getOrganizationName())
                    .orElseGet(() -> organizationRepository.save(org));
            map.put(o.getOrganizationName(), o);
        }
        return map;
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private Set<Role> roleSet(Map<UserRole, Role> roles, UserRole... types) {
        return Arrays.stream(types)
                .map(roles::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record UserSeed(AppUser user, Organization org) {}

    private void seedUsers(Map<UserRole, Role> roles, Map<String, Organization> orgs) {
        List<UserSeed> seeds = List.of(
                new UserSeed(AppUser.builder()
                        .firstName("Elikem").lastName("Fenuku")
                        .username("kingmaker")
                        .appUserEmail("elikemfenuksu@gmail.com")
                        .organizationName("Electoral Commission")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.SUPER_ADMIN)
                        .roles(new HashSet<>(roles.values()))
                        .emailVerified(true)
                        .build(), orgs.get("Electoral Commission")),

                new UserSeed(AppUser.builder()
                        .firstName("Elikem").lastName("Fenuku")
                        .username("reynolds")
                        .appUserEmail("reynoldsfenuku@gmail.com")
                        .organizationName("Electoral Commission")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.SUPER_ADMIN)
                        .roles(new HashSet<>(roles.values()))
                        .emailVerified(true)
                        .build(), orgs.get("Electoral Commission")),

                new UserSeed(AppUser.builder()
                        .firstName("Ekow").lastName("Davis")
                        .username("davis")
                        .appUserEmail("davis@brandedhustlers.com")
                        .organizationName("Electoral Commission")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.ADMIN)
                        .roles(roleSet(roles, UserRole.ADMIN))
                        .emailVerified(true)
                        .build(), orgs.get("Electoral Commission")),

                new UserSeed(AppUser.builder()
                        .firstName("Nat").lastName("Auditor")
                        .username("auditor")
                        .appUserEmail("auditor@civic.local")
                        .organizationName("Open Civic Alliance")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.AUDITOR)
                        .roles(roleSet(roles, UserRole.AUDITOR))
                        .emailVerified(true)
                        .build(), orgs.get("Open Civic Alliance")),

                new UserSeed(AppUser.builder()
                        .firstName("Joel").lastName("Contributor")
                        .username("contrib")
                        .appUserEmail("contrib@demoparty.local")
                        .organizationName("Demo Party")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.CONTRIBUTOR)
                        .roles(roleSet(roles, UserRole.CONTRIBUTOR))
                        .emailVerified(true)
                        .build(), orgs.get("Demo Party")),

                new UserSeed(AppUser.builder()
                        .firstName("Roman").lastName("Observer")
                        .username("reigns")
                        .appUserEmail("observer@civic.local")
                        .organizationName("Open Civic Alliance")
                        .password(encoder.encode("test"))
                        .userRole(UserRole.OBSERVER)
                        .roles(roleSet(roles, UserRole.OBSERVER))
                        .emailVerified(true)
                        .build(), orgs.get("Open Civic Alliance")));

        for (UserSeed seed : seeds) {
            AppUser u = seed.user();
            boolean exists = userRepository.findByUsernameOrEmail(
                    StringUtils.hasText(u.getAppUserEmail())
                            ? u.getAppUserEmail()
                            : u.getUsername()).isPresent();
            if (!exists) {
                AppUser saved = userRepository.save(u);
                userOrganizationRepository.save(UserOrganization.builder()
                        .appUser(saved)
                        .organization(seed.org())
                        .roleInOrg(saved.getUserRole().name())
                        .activeOrg(true)
                        .build());
            }
        }
    }
}
