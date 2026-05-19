package com.brandedhustlers.electionbackend.auth;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.RefreshToken;
import com.brandedhustlers.electionbackend.auth.dto.LoginRequest;
import com.brandedhustlers.electionbackend.auth.dto.LoginResponse;
import com.brandedhustlers.electionbackend.auth.dto.TokenRefreshRequest;
import com.brandedhustlers.electionbackend.auth.dto.TokenRefreshResponse;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.mapper.AppUserMapper;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.auth.repository.RoleRepository;
import com.brandedhustlers.electionbackend.auth.repository.UserOrganizationRepository;
import com.brandedhustlers.electionbackend.auth.service.*;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import com.brandedhustlers.electionbackend.common.config.storage.service.CloudinaryService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.common.exceptions.RefreshTokenException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService (extended)")
class AuthServiceExtendedTest {

    @Mock
    private JWTService jwtService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private GetUser getUser;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserOrganizationRepository userOrganizationRepository;
    @Mock
    private MessagingService messagingService;
    @Mock
    private MfaService mfaService;
    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private AuditEmitter auditEmitter;

    @Mock
    private CacheManager cacheManager;
    @Mock
    private UserCacheDelegate userCacheDelegate;

    private AuthService authService;
    private UUID userId;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                jwtService, appUserRepository, passwordEncoder, authenticationManager,
                cloudinaryService, httpServletRequest, roleRepository, getUser,
                organizationService, organizationMapper, organizationMapper,
                appUserMapper, organizationRepository, refreshTokenService,
                userOrganizationRepository, messagingService, mfaService, auditEmitter, userCacheDelegate, cacheManager);

        ReflectionTestUtils.setField(authService, "appBaseUrl", "https://app.test");

        userId = UUID.randomUUID();
        testUser = AppUser.builder()
                .appUserId(userId)
                .firstName("Jane")
                .lastName("Doe")
                .appUserEmail("jane@test.com")
                .username("janedoe")
                .password("encoded-pw")
                .emailVerified(true)
                .mfaEnabled(false)
                .build();
    }

    // ─── verifyEmail ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("Marks email as verified and clears token when token is valid")
        void marksVerifiedOnValidToken() {
            testUser.setEmailVerificationToken("valid-token");
            testUser.setEmailVerificationExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            when(appUserRepository.findByEmailVerificationToken("valid-token"))
                    .thenReturn(Optional.of(testUser));

            authService.verifyEmail("valid-token");

            assertTrue(testUser.isEmailVerified());
            assertNull(testUser.getEmailVerificationToken());
            assertNull(testUser.getEmailVerificationExpiry());
            verify(appUserRepository).save(testUser);
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when token is not found")
        void throwsForUnknownToken() {
            when(appUserRepository.findByEmailVerificationToken("bad-token"))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("bad-token"));
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when token has expired")
        void throwsForExpiredToken() {
            testUser.setEmailVerificationToken("expired-token");
            testUser.setEmailVerificationExpiry(Instant.now().minus(1, ChronoUnit.HOURS));
            when(appUserRepository.findByEmailVerificationToken("expired-token"))
                    .thenReturn(Optional.of(testUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.verifyEmail("expired-token"));

            assertTrue(ex.getMessage().contains("expired"));
        }
    }

    // ─── resendVerificationEmail ───────────────────────────────────────────────

    @Nested
    @DisplayName("resendVerificationEmail")
    class ResendVerificationEmail {

        @Test
        @DisplayName("Regenerates token and sends email when account is unverified")
        void regeneratesTokenAndSendsEmail() throws Exception {
            testUser.setEmailVerified(false);
            when(appUserRepository.findByAppUserEmail("jane@test.com"))
                    .thenReturn(Optional.of(testUser));

            authService.resendVerificationEmail("jane@test.com");

            verify(appUserRepository).save(testUser);
            verify(messagingService).sendVerificationEmail(eq("jane@test.com"), eq("Jane"), anyString());
        }

        @Test
        @DisplayName("Throws IllegalStateException when email is already verified")
        void throwsWhenAlreadyVerified() {
            testUser.setEmailVerified(true);
            when(appUserRepository.findByAppUserEmail("jane@test.com"))
                    .thenReturn(Optional.of(testUser));

            assertThrows(IllegalStateException.class,
                    () -> authService.resendVerificationEmail("jane@test.com"));

            verifyNoInteractions(messagingService);
        }

        @Test
        @DisplayName("Throws UserNotFound when no account matches email")
        void throwsWhenEmailNotFound() {
            when(appUserRepository.findByAppUserEmail("ghost@test.com"))
                    .thenReturn(Optional.empty());

            assertThrows(UserNotFound.class,
                    () -> authService.resendVerificationEmail("ghost@test.com"));
        }
    }

    // ─── forgotPassword ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("forgotPassword")
    class ForgotPassword {

        @Test
        @DisplayName("Sets reset token and sends email when account exists")
        void setsTokenAndSendsEmail() {
            when(appUserRepository.findByAppUserEmail("jane@test.com"))
                    .thenReturn(Optional.of(testUser));

            authService.forgotPassword("jane@test.com");

            verify(appUserRepository).save(testUser);
            verify(messagingService).sendPasswordResetEmail(eq("jane@test.com"), eq("Jane"), anyString());
            assertNotNull(testUser.getPasswordResetToken());
        }

        @Test
        @DisplayName("Does nothing when email is not found (prevents user enumeration)")
        void doesNothingWhenEmailNotFound() {
            when(appUserRepository.findByAppUserEmail("ghost@test.com"))
                    .thenReturn(Optional.empty());

            // Must not throw
            assertDoesNotThrow(() -> authService.forgotPassword("ghost@test.com"));
            verifyNoInteractions(messagingService);
        }
    }

    // ─── resetPassword ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("Encodes new password and clears reset token on success")
        void resetsPasswordOnValidToken() {
            testUser.setPasswordResetToken("reset-token");
            testUser.setPasswordResetExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            when(appUserRepository.findByPasswordResetToken("reset-token"))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("newPass", testUser.getPassword())).thenReturn(false);
            when(passwordEncoder.encode("newPass")).thenReturn("new-encoded");

            authService.resetPassword("reset-token", "newPass");

            assertEquals("new-encoded", testUser.getPassword());
            assertNull(testUser.getPasswordResetToken());
            assertNull(testUser.getPasswordResetExpiry());
            verify(appUserRepository).save(testUser);
        }

        @Test
        @DisplayName("Throws IllegalArgumentException for unknown reset token")
        void throwsForUnknownToken() {
            when(appUserRepository.findByPasswordResetToken("bad")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> authService.resetPassword("bad", "newPass"));
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when reset token has expired")
        void throwsForExpiredToken() {
            testUser.setPasswordResetToken("expired-token");
            testUser.setPasswordResetExpiry(Instant.now().minus(1, ChronoUnit.HOURS));
            when(appUserRepository.findByPasswordResetToken("expired-token"))
                    .thenReturn(Optional.of(testUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.resetPassword("expired-token", "newPass"));

            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when new password is same as current")
        void throwsWhenSamePassword() {
            testUser.setPasswordResetToken("reset-token");
            testUser.setPasswordResetExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            when(appUserRepository.findByPasswordResetToken("reset-token"))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("samePass", testUser.getPassword())).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.resetPassword("reset-token", "samePass"));

            assertTrue(ex.getMessage().contains("differ"));
        }
    }

    // ─── enableMfa / disableMfa ────────────────────────────────────────────────

    @Nested
    @DisplayName("enableMfa and disableMfa")
    class MfaToggle {

        @Test
        @DisplayName("Sets mfaEnabled=true and saves user")
        void enablesMfa() {
            testUser.setMfaEnabled(false);
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(testUser);

            authService.enableMfa();

            assertTrue(testUser.isMfaEnabled());
            verify(appUserRepository).save(testUser);
        }

        @Test
        @DisplayName("Sets mfaEnabled=false and saves user")
        void disablesMfa() {
            testUser.setMfaEnabled(true);
            when(getUser.getCurrentAuthenticatedUser()).thenReturn(testUser);

            authService.disableMfa();

            assertFalse(testUser.isMfaEnabled());
            verify(appUserRepository).save(testUser);
        }
    }

    // ─── login (MFA branch) ────────────────────────────────────────────────────

    @Nested
    @DisplayName("login — MFA challenge branch")
    class LoginMfaBranch {

        @Test
        @DisplayName("Returns mfaChallenge response when user has MFA enabled")
        void returnsMfaChallengeWhenMfaEnabled() {
            testUser.setMfaEnabled(true);

            UserPrincipal principal = mock(UserPrincipal.class);
            when(principal.getAppUserId()).thenReturn(userId);
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(principal);
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(mfaService.initiateMfa(userId)).thenReturn("mfa-pending-token");

            LoginRequest req = new LoginRequest(null, "jane@test.com", "pass");
            LoginResponse response = authService.login(req, httpServletResponse);

            assertTrue(response.mfaRequired());
            assertEquals("mfa-pending-token", response.mfaToken());
            verifyNoInteractions(refreshTokenService);
        }

        @Test
        @DisplayName("Returns standard token response when MFA is disabled")
        void returnsStandardResponseWhenMfaDisabled() throws Exception {
            testUser.setMfaEnabled(false);

            UserPrincipal principal = mock(UserPrincipal.class);
            when(principal.getAppUserId()).thenReturn(userId);
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(principal);
            when(authenticationManager.authenticate(any())).thenReturn(auth);
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(anyString(), any(), anyString())).thenReturn("access-token");
            RefreshToken rt = RefreshToken.builder().token("refresh-token").build();
            when(refreshTokenService.createRefreshToken(userId)).thenReturn(rt);

            LoginRequest req = new LoginRequest(null, "jane@test.com", "pass");
            LoginResponse response = authService.login(req, httpServletResponse);

            assertFalse(response.mfaRequired());
            assertEquals("access-token", response.accessToken());
        }
    }

    // ─── refreshToken ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenFlow {

        @Test
        @DisplayName("Returns new access token for valid refresh token")
        void returnsNewAccessToken() {
            RefreshToken rt = RefreshToken.builder()
                    .token("rt-value")
                    .expiryDate(Instant.now().plusSeconds(3600))
                    .user(testUser)
                    .build();

            when(refreshTokenService.findByToken("rt-value")).thenReturn(Optional.of(rt));
            when(refreshTokenService.verifyExpiration(rt)).thenReturn(rt);
            when(jwtService.generateAccessToken(anyString(), any(), anyString())).thenReturn("new-access");

            TokenRefreshResponse response = authService.refreshToken(new TokenRefreshRequest("rt-value"));

            assertEquals("new-access", response.accessToken());
            assertEquals("rt-value", response.refreshToken());
        }

        @Test
        @DisplayName("Throws RefreshTokenException when token not found in DB")
        void throwsWhenTokenNotFound() {
            when(refreshTokenService.findByToken("missing")).thenReturn(Optional.empty());

            assertThrows(RefreshTokenException.class,
                    () -> authService.refreshToken(new TokenRefreshRequest("missing")));
        }
    }
}
