package com.brandedhustlers.electionbackend.auth;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyRequest;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.service.MfaService;
import com.brandedhustlers.electionbackend.auth.service.RefreshTokenService;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MfaService")
class MfaServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private MessagingService messagingService;
    @Mock
    private JWTService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private MfaService mfaService;
    private UUID userId;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService(
                appUserRepository, messagingService, jwtService, refreshTokenService,
                redis, new ObjectMapper());

        userId = UUID.randomUUID();
        testUser = AppUser.builder()
                .appUserId(userId)
                .firstName("John")
                .appUserEmail("john@test.com")
                .username("johndoe")
                .build();

        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("initiateMfa")
    class InitiateMfa {

        @Test
        @DisplayName("Stores OTP in Redis, sends email, and returns MFA pending token")
        void storesOtpSendsEmailAndReturnsToken() throws Exception {
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(jwtService.generateMfaPendingToken(userId, "john@test.com"))
                    .thenReturn("mfa-pending-jwt");

            String result = mfaService.initiateMfa(userId);

            assertEquals("mfa-pending-jwt", result);
            verify(valueOps).set(eq("mfa:otp:" + userId), anyString(), eq(600L), any());
            verify(messagingService).sendOtpEmail(eq("john@test.com"), eq("John"), anyString());
        }

        @Test
        @DisplayName("OTP stored in Redis is a 6-digit string")
        void otpIsSixDigits() throws Exception {
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(jwtService.generateMfaPendingToken(any(), any())).thenReturn("token");

            mfaService.initiateMfa(userId);

            // Capture the OTP sent to the email service
            var otpCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(messagingService).sendOtpEmail(any(), any(), otpCaptor.capture());

            String capturedOtp = otpCaptor.getValue();
            assertEquals(6, capturedOtp.length());
            assertTrue(capturedOtp.matches("\\d{6}"),
                    "OTP should be exactly 6 digits, got: " + capturedOtp);
        }

        @Test
        @DisplayName("Throws UserNotFound when user does not exist")
        void throwsUserNotFoundWhenUserMissing() {
            when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(UserNotFound.class, () -> mfaService.initiateMfa(userId));
            verifyNoInteractions(messagingService, jwtService);
        }

        @Test
        @DisplayName("Still returns MFA token even if email sending fails")
        void returnsTokenEvenIfEmailFails() throws Exception {
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(jwtService.generateMfaPendingToken(any(), any())).thenReturn("mfa-token");
            doThrow(new jakarta.mail.MessagingException("SMTP error"))
                    .when(messagingService).sendOtpEmail(any(), any(), any());

            // Should NOT throw — email failure is caught and logged
            String result = assertDoesNotThrow(() -> mfaService.initiateMfa(userId));
            assertEquals("mfa-token", result);
        }
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("Throws when MFA token is not a pending token")
        void throwsWhenNotMfaPendingToken() {
            when(jwtService.isMfaPendingToken("bad-token")).thenReturn(false);

            MfaVerifyRequest req = new MfaVerifyRequest("bad-token", "123456");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mfaService.verify(req));

            assertTrue(ex.getMessage().contains("Invalid MFA token"));
        }

        @Test
        @DisplayName("Throws when MFA token is expired")
        void throwsWhenMfaTokenExpired() {
            when(jwtService.isMfaPendingToken("expired-token")).thenReturn(true);
            when(jwtService.isTokenExpired("expired-token")).thenReturn(true);

            MfaVerifyRequest req = new MfaVerifyRequest("expired-token", "123456");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mfaService.verify(req));

            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("Throws when OTP has expired in Redis (null response)")
        void throwsWhenOtpExpiredInRedis() {
            when(jwtService.isMfaPendingToken("valid-token")).thenReturn(true);
            when(jwtService.isTokenExpired("valid-token")).thenReturn(false);
            when(jwtService.extractMfaPendingUserId("valid-token")).thenReturn(userId);
            when(valueOps.get("mfa:otp:" + userId)).thenReturn(null);

            MfaVerifyRequest req = new MfaVerifyRequest("valid-token", "123456");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mfaService.verify(req));

            assertTrue(ex.getMessage().contains("expired") || ex.getMessage().contains("OTP"));
        }

        @Test
        @DisplayName("Throws when userId cannot be extracted from MFA token")
        void throwsWhenUserIdMissing() {
            when(jwtService.isMfaPendingToken("valid-token")).thenReturn(true);
            when(jwtService.isTokenExpired("valid-token")).thenReturn(false);
            when(jwtService.extractMfaPendingUserId("valid-token")).thenReturn(null);

            MfaVerifyRequest req = new MfaVerifyRequest("valid-token", "123456");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> mfaService.verify(req));

            assertTrue(ex.getMessage().contains("missing user identifier"));
        }

        @Test
        @DisplayName("Throws and deletes Redis key when max attempts exceeded")
        void throwsAndDeletesKeyWhenMaxAttemptsExceeded() {
            // attempts = 3 means max already hit
            String json = "{\"otpHash\":\"doesnotmatter\",\"attempts\":3}";
            when(jwtService.isMfaPendingToken("valid-token")).thenReturn(true);
            when(jwtService.isTokenExpired("valid-token")).thenReturn(false);
            when(jwtService.extractMfaPendingUserId("valid-token")).thenReturn(userId);
            when(valueOps.get("mfa:otp:" + userId)).thenReturn(json);

            MfaVerifyRequest req = new MfaVerifyRequest("valid-token", "000000");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> mfaService.verify(req));

            assertTrue(ex.getMessage().contains("Too many"));
            verify(redis).delete("mfa:otp:" + userId);
        }
    }
}
