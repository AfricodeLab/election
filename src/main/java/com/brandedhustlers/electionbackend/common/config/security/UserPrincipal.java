package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {
    private final Collection<? extends GrantedAuthority> authorities;

    private final AppUser appUser;

    public UserPrincipal(Collection<? extends GrantedAuthority> authorities, AppUser appUser) {
        this.authorities = authorities;
        this.appUser = appUser;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority(appUser.getUserRole().name())
        );
    }

    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    public UUID getAppUserId(){
        return appUser.getAppUserId();
    }

    @Override
    public String getUsername() {
        return appUser.getAppUserEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
