package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyRequest;
import com.brandedhustlers.electionbackend.auth.dto.MfaVerifyResponse;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MFA service for operator accounts (ADMIN, ORG_ADMIN, SUPER_ADMIN).
 *
 * <p>OTPs are stored in-memory for simplicity. In a multi-instance deployment
 * this should be replaced with a Redis-backed store (e.g. using Spring Data Redis
 * with a short TTL key).
 */
@Slf4j
@Service
public class MfaService {

    private static final int OTP_TTL_SECONDS = 600;  // 10 min
    private static final int MAX_ATTEMPTS = 3;

    private final AppUserRepository appUserRepository;
    private final MessagingService messagingService;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;

    /** userId → pending OTP entry (in-memory; swap for Redis in multi-node) */
    private final ConcurrentHashMap<UUID, OtpEntry> pendingOtps = new ConcurrentHashMap<>();
    private final SecureRandom rng = new SecureRandom();

    public MfaService(AppUserRepository appUserRepository,
                      MessagingService messagingService,
                      JWTService jwtService,
                      RefreshTokenService refreshTokenService) {
        this.appUserRepository = appUserRepository;
        this.messagingService = messagingService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Generates a 6-digit OTP, stores it (hashed), sends it by email,
     * and returns a short-lived MFA-pending JWT the client must include
     * when calling /auth/mfa/verify.
     */
    public String initiateMfa(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        String otp = String.valueOf(100_000 + rng.nextInt(900_000));
        String otpHash = sha256(otp);
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);

        pendingOtps.put(userId, new OtpEntry(otpHash, expiresAt, 0));

        try {
            messagingService.sendOtpEmail(user.getAppUserEmail(), user.getFirstName(), otp);
        } catch (MessagingException e) {
            log.error("Failed to send MFA OTP to {}: {}", user.getAppUserEmail(), e.getMessage());
            // Don't throw — caller already generated the mfaToken;
            // the user can request a new one via login again.
        }

        return jwtService.generateMfaPendingToken(userId, user.getAppUserEmail());
    }

    /**
     * Validates the MFA-pending token + OTP, then issues a full access + refresh token pair.
     */
    public MfaVerifyResponse verify(MfaVerifyRequest req) {
        String mfaToken = req.mfaToken();

        if (!jwtService.isMfaPendingToken(mfaToken)) {
            throw new IllegalArgumentException("Invalid MFA token");
        }
        if (jwtService.isTokenExpired(mfaToken)) {
            throw new IllegalArgumentException("MFA session expired. Please log in again.");
        }

        UUID userId = jwtService.extractMfaPendingUserId(mfaToken);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid MFA token: missing user identifier");
        }

        OtpEntry entry = pendingOtps.get(userId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            pendingOtps.remove(userId);
            throw new IllegalArgumentException("OTP has expired. Please log in again to receive a new code.");
        }

        if (entry.attempts() >= MAX_ATTEMPTS) {
            pendingOtps.remove(userId);
            throw new IllegalStateException("Too many incorrect attempts. Please log in again.");
        }

        if (!sha256(req.otp()).equals(entry.otpHash())) {
            pendingOtps.put(userId, entry.incrementAttempts());
            int remaining = MAX_ATTEMPTS - entry.attempts() - 1;
            throw new IllegalArgumentException("Incorrect OTP. Attempts remaining: " + remaining);
        }

        // OTP correct — clear entry and issue full tokens
        pendingOtps.remove(userId);

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        String accessToken = jwtService.generateAccessToken(
                user.getAppUserEmail(), user.getAppUserId(), user.getAppUserEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user.getAppUserId()).getToken();

        log.info("MFA verification successful for user {}", userId);

        return new MfaVerifyResponse(true, accessToken, refreshToken,
                user.getUsername(), user.getAppUserEmail());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

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

    private record OtpEntry(String otpHash, Instant expiresAt, int attempts) {
        OtpEntry incrementAttempts() {
            return new OtpEntry(otpHash, expiresAt, attempts + 1);
        }
    }
}
