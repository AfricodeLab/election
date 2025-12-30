package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AppUser> appUser = appUserRepository.findByUsernameOrEmail((username));
        if (appUser.isPresent()){
            return new UserPrincipal(getAuthorities(appUser.get()), appUser.get());
        }
        throw new UserNotFound("User account not found.");
    }

    private Collection<? extends GrantedAuthority> getAuthorities(AppUser user){
        String prefix = "ROLE_";
        Set<SimpleGrantedAuthority> set = user.getRoles()
                .stream()
                .map(Role::getRoleName)
                .map(String::toUpperCase)
                .map(StringBuilder::new)//convert to stringbuilder so I can prepend
                .map(authRole -> authRole.insert(0, prefix)) //prepend role prefix
                .map(StringBuilder::toString)//converting stringbuilder to tring
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        log.info("user roles: {}", set);
        return set;
    }
}
