package com.brandedhustlers.electionbackend.common.security;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService")
class CustomUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(appUserRepository);
    }

    private AppUser activeVerifiedUser() {
        return AppUser.builder()
                .appUserId(UUID.randomUUID())
                .appUserEmail("user@test.com")
                .username("testuser")
                .password("encoded-password")
                .deleteYn(DeleteYn.N)
                .emailVerified(true)
                .roles(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("Returns UserPrincipal for active, verified user")
    void returnsUserPrincipalForValidUser() {
        AppUser user = activeVerifiedUser();
        when(appUserRepository.findByUsernameOrEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("user@test.com");

        assertNotNull(result);
        assertEquals("user@test.com", result.getUsername());
    }

    @Test
    @DisplayName("Throws UsernameNotFoundException for deactivated account (DeleteYn.Y)")
    void throwsForDeactivatedUser() {
        AppUser user = activeVerifiedUser();
        user.setDeleteYn(DeleteYn.Y);
        when(appUserRepository.findByUsernameOrEmail("user@test.com")).thenReturn(Optional.of(user));

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("user@test.com"));

        assertTrue(ex.getMessage().contains("deactivated"));
    }

    @Test
    @DisplayName("Throws UsernameNotFoundException for unverified email")
    void throwsForUnverifiedEmail() {
        AppUser user = activeVerifiedUser();
        user.setEmailVerified(false);
        when(appUserRepository.findByUsernameOrEmail("user@test.com")).thenReturn(Optional.of(user));

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("user@test.com"));

        assertTrue(ex.getMessage().contains("not verified"));
    }

    @Test
    @DisplayName("Throws UserNotFound when no account matches username or email")
    void throwsUserNotFoundWhenMissing() {
        when(appUserRepository.findByUsernameOrEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFound.class, () -> service.loadUserByUsername("ghost@test.com"));
    }
}
