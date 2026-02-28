package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.brandedhustlers.electionbackend.voter.domain.VerificationToken;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmResponse;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendResponse;
import com.brandedhustlers.electionbackend.voter.repository.VerificationTokenRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

@Slf4j
@Service
public class VerificationService {
    private final VoterRepository voters;
    private final VerificationTokenRepository tokens;
    private final MessagingService notif; // inject a stub if module not present
    private final JWTService jwtService;

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;
    public static final String SESSION_KEY = "STEP_UP_VERIFIED_AT";
    private final SecureRandom rnd = new SecureRandom();

    public VerificationService(VoterRepository voters,
            VerificationTokenRepository tokens,
            MessagingService notif, JWTService jwtService) {
        this.voters = voters;
        this.tokens = tokens;
        this.notif = notif;
        this.jwtService = jwtService;
    }

    @Transactional
    public OtpSendResponse sendOtp(OtpSendRequest req) throws MessagingException {
        var voter = voters.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new NoSuchElementException("VOTER-404 not found"));

        var now = Instant.now();
        var last = tokens.findValidToken(voter.getVoterId(), now)
                .orElse(null);

        if (last != null && Duration.between(last.getCreatedAt(), now).compareTo(RESEND_COOLDOWN) < 0) {
            last.setDeleteYn(DeleteYn.Y);
            tokens.save(last);
            throw new IllegalStateException("OTP-429 resend too soon, please wait");
        }

        String otp = generateOtp6();
        log.info("otp generated for voter with email: {}", voter.getEmail());
        String hash = sha256(otp);

        var t = VerificationToken.builder()
                .voterId(voter.getVoterId())
                .tokenHash(hash)
                .type("OTP")
                .channel("EMAIL")
                .attempts(0)
                .createdAt(now)
                .expiresAt(now.plus(OTP_TTL))
                .isUsed(false)
                .build();
        tokens.save(t);

        // Send email directly
        notif.sendOtpEmail(voter.getEmail(), voter.getName(), otp);

        // todo emit audit log here

        return new OtpSendResponse(t.getId(), "EMAIL");
    }

    public OtpConfirmResponse confirmOtp(OtpConfirmRequest req, HttpServletResponse response) {
        var voter = voters.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new NoSuchElementException("Voter not found"));

        var now = Instant.now();
        var tok = tokens.findValidToken(voter.getVoterId(), now)
                .orElseThrow(() -> new NoSuchElementException("No active code"));

        if (tok.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many attempts");
        }
        String hash = sha256(req.otp());
        if (!hash.equals(tok.getTokenHash())) {
            tok.setAttempts(tok.getAttempts() + 1);
            tokens.save(tok);
            return new OtpConfirmResponse(false, "Incorrect code. Attempts left: " + (MAX_ATTEMPTS - tok.getAttempts()),
                    null);
        }

        tok.setUsed(true);
        tok.setDeleteYn(DeleteYn.Y);
        tokens.save(tok);

        String voterToken = jwtService.generateVoterToken(voter.getEmail());

        ResponseCookie jwtCookie = ResponseCookie.from("voterToken", voterToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(3600)
                .build();
        response.setHeader("Set-Cookie", jwtCookie.toString());

        return new OtpConfirmResponse(true,
                "Voter verified",
                voterToken);
    }

    private String generateOtp6() {
        int code = rnd.nextInt(900000) + 100000;
        return Integer.toString(code);
    }

    private static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
