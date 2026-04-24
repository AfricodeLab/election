package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AppUser> appUser = appUserRepository.findByUsernameOrEmail(username);
        if (appUser.isPresent()) {
            AppUser user = appUser.get();
            if (DeleteYn.Y.equals(user.getDeleteYn())) {
                log.warn("Login attempt for deactivated account: {}", username);
                throw new UsernameNotFoundException("User account has been deactivated.");
            }
            if (!user.isEmailVerified()) {
                log.warn("Login attempt for unverified account: {}", username);
                throw new UsernameNotFoundException("Email address not verified. Please check your inbox for the verification link.");
            }

            return new UserPrincipal(getAuthorities(user), user);
        }
        throw  new UserNotFound("User account not found.");
    }

    private Collection<? extends GrantedAuthority> getAuthorities(AppUser user) {
        Set<SimpleGrantedAuthority> set = new HashSet<>();

        // 1. ROLE_ authorities — every role entity the user holds (system + custom org roles).
        //    getAuthorities() uppercases the stored name so "Election_Manager" → ROLE_ELECTION_MANAGER.
        user.getRoles().stream()
                .map(Role::getRoleName)
                .map(String::toUpperCase)
                .map(name -> "ROLE_" + name)
                .map(SimpleGrantedAuthority::new)
                .forEach(set::add);

        // 2. The userRole enum field is always included as a safety floor — it guarantees
        //    the base system role authority even if the roles Set is somehow empty.
        if (user.getUserRole() != null) {
            set.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));
        }

        // 3. PERM_ authorities — collected from every role that applies to the user's
        //    current org context. System roles (orgId = null) apply everywhere; org-scoped
        //    custom roles apply only while the user is active in that org.
        //    These authorities let SecurityConfig grant HTTP-gate access based on a
        //    specific permission rather than a hardcoded system role name, allowing
        //    ORG_ADMINs to delegate access to trusted CONTRIBUTOR/AUDITOR/OBSERVER users
        //    via custom roles without changing compiled code.
        UUID activeOrgId = Optional.ofNullable(user.getActiveOrganization())
                .map(Organization::getOrganizationId)
                .orElse(null);

        user.getRoles().stream()
                .filter(role -> role.getOrgId() == null
                        || role.getOrgId().equals(activeOrgId))
                .flatMap(role -> role.getRolePermissions().stream())
                .map(perm -> "PERM_" + perm.getPermissionType().name())
                .map(SimpleGrantedAuthority::new)
                .forEach(set::add);

        log.info("user authorities: {}", set);
        return set;
    }
}
