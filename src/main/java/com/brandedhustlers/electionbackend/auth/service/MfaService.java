package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyRequest;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyResponse;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MFA service for operator accounts (ADMIN, ORG_ADMIN, SUPER_ADMIN).
 * OTPs are stored in Redis with a 10-minute TTL — safe across restarts
 * and multiple instances.
 */
@Slf4j
@Service
public class MfaService {

    private static final int OTP_TTL_SECONDS = 600;
    private static final int MAX_ATTEMPTS = 3;
    private static final String KEY_PREFIX = "mfa:otp:";

    private final AppUserRepository appUserRepository;
    private final MessagingService messagingService;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SecureRandom rng = new SecureRandom();

    @Value("${app.email.environment}")
    private String environment;

    public MfaService(AppUserRepository appUserRepository,
                      MessagingService messagingService,
                      JWTService jwtService,
                      RefreshTokenService refreshTokenService,
                      StringRedisTemplate redis,
                      ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.messagingService = messagingService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a 6-digit OTP, stores it (hashed) in Redis with a 10-min TTL,
     * sends it by email, and returns a short-lived MFA-pending JWT.
     */
    public String initiateMfa(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        String otp = String.valueOf(100_000 + rng.nextInt(900_000));
        storeOtp(userId, new OtpEntry(sha256(otp), 0));

        try {
            if(environment.equalsIgnoreCase("development")){
                messagingService.sendOtpEmail(user.getAppUserEmail(),
                        user.getFirstName(), otp);
            }else{
                messagingService.sendOtpEmailWithResend(user.getAppUserEmail(),
                        user.getFirstName(), otp);
            }
        } catch (MessagingException e) {
            log.error("Failed to send MFA OTP to {}: {}", user.getAppUserEmail(), e.getMessage());
        }

        return jwtService.generateMfaPendingToken(userId, user.getAppUserEmail());
    }

    /**
     * Validates the MFA-pending token + OTP, then issues full access + refresh tokens.
     */
    public MfaVerifyResponse verify(MfaVerifyRequest req) {
        if (!jwtService.isMfaPendingToken(req.mfaToken())) {
            throw new IllegalArgumentException("Invalid MFA token");
        }
        if (jwtService.isTokenExpired(req.mfaToken())) {
            throw new IllegalArgumentException("MFA session expired. Please log in again.");
        }

        UUID userId = jwtService.extractMfaPendingUserId(req.mfaToken());
        if (userId == null) {
            throw new IllegalArgumentException("Invalid MFA token: missing user identifier");
        }

        OtpEntry entry = loadOtp(userId);
        if (entry == null) {
            throw new IllegalArgumentException("OTP has expired. Please log in again to receive a new code.");
        }
        if (entry.attempts() >= MAX_ATTEMPTS) {
            redis.delete(KEY_PREFIX + userId);
            throw new IllegalStateException("Too many incorrect attempts. Please log in again.");
        }
        if (!sha256(req.otp()).equals(entry.otpHash())) {
            Long ttl = redis.getExpire(KEY_PREFIX + userId, TimeUnit.SECONDS);
            storeOtp(userId, entry.incrementAttempts(), ttl != null && ttl > 0 ? ttl : OTP_TTL_SECONDS);
            int remaining = MAX_ATTEMPTS - entry.attempts() - 1;
            throw new IllegalArgumentException("Incorrect OTP. Attempts remaining: " + remaining);
        }

        redis.delete(KEY_PREFIX + userId);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        String accessToken = jwtService.generateAccessToken(
                user.getAppUserEmail(), user.getAppUserId(), user.getAppUserEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user.getAppUserId()).getToken();

        log.info("MFA verification successful for user {}", userId);
        return new MfaVerifyResponse(true, accessToken, refreshToken,
                user.getUsername(), user.getAppUserEmail());
    }

    // ── Redis helpers ──────────────────────────────────────────────────────────

    private void storeOtp(UUID userId, OtpEntry entry) {
        storeOtp(userId, entry, OTP_TTL_SECONDS);
    }

    private void storeOtp(UUID userId, OtpEntry entry, long ttlSeconds) {
        try {
            redis.opsForValue().set(KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(entry),
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store MFA OTP", e);
        }
    }

    private OtpEntry loadOtp(UUID userId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + userId);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, OtpEntry.class);
        } catch (Exception e) {
            log.error("Failed to deserialise MFA OTP for user {}", userId);
            return null;
        }
    }

    // ── Crypto helper ──────────────────────────────────────────────────────────

    private static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    record OtpEntry(String otpHash, int attempts) {
        OtpEntry incrementAttempts() {
            return new OtpEntry(otpHash, attempts + 1);
        }
    }
}
