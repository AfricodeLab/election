package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.exceptions.UserNotFound;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<AppUser> appUser = appUserRepository.findByAppUserName((username));
        if (appUser.isPresent()){
            return new UserPrincipal(getAuthorities(appUser.get()), appUser.get());
        }
        throw new UserNotFound("User account not found.");
    }

    private Collection<? extends GrantedAuthority> getAuthorities(AppUser user){
        String userRole = "ROLE_" + user.getUserRole();
        return List.of(new SimpleGrantedAuthority(userRole));
    }

}
