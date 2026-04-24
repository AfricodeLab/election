package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.notifications.service.MessagingService;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import com.brandedhustlers.electionbackend.voter.domain.ElectionVoterId;
import com.brandedhustlers.electionbackend.voter.domain.VerificationToken;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.*;
import com.brandedhustlers.electionbackend.voter.repository.ElectionVoterRepository;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class VerificationService implements IVerificationService {

    private final VoterRepository voters;
    private final VerificationTokenRepository tokens;
    private final MessagingService notif;
    private final JWTService jwtService;
    private final ElectionRepository electionRepository;
    private final ElectionVoterRepository evRepo;
    private final AuditEmitter emitter;

    private static final Duration OTP_TTL          = Duration.ofMinutes(10);
    private static final Duration STATION_PIN_TTL   = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN   = Duration.ofSeconds(30);
    private static final int      MAX_ATTEMPTS      = 3;

    /** Legacy session key kept for backward compatibility. */
    public static final String SESSION_KEY = "STEP_UP_VERIFIED_AT";

    private final SecureRandom rnd = new SecureRandom();

    public VerificationService(VoterRepository voters,
                               VerificationTokenRepository tokens,
                               MessagingService notif,
                               JWTService jwtService,
                               ElectionRepository electionRepository,
                               ElectionVoterRepository evRepo, AuditEmitter emitter) {
        this.voters = voters;
        this.tokens = tokens;
        this.notif = notif;
        this.jwtService = jwtService;
        this.electionRepository = electionRepository;
        this.evRepo = evRepo;
        this.emitter = emitter;
    }

    // ── Remote OTP flow ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public OtpSendResponse sendOtp(OtpSendRequest req) throws MessagingException {

        // 1. Look up voter by email
        Voter voter = voters.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new NoSuchElementException("No voter registered with that email address"));

        // 2. Validate national ID — identity pre-check before dispatching anything
        if (voter.getNationalId() == null
                || !voter.getNationalId().trim().equalsIgnoreCase(req.nationalId().trim())) {
            // Same generic message for both "no national ID on file" and "mismatch" —
            // avoid leaking which field is wrong to a potential attacker.
            throw new IllegalArgumentException("Email and National ID do not match our records");
        }

        // 3. Confirm the election exists (only when scoped to a specific election)
        if (req.electionId() != null) {
            electionRepository.findById(req.electionId())
                    .orElseThrow(() -> new NoSuchElementException("Election not found"));
        }

        // 4. Resend cooldown — prevent spam
        var now = Instant.now();
        Optional<VerificationToken> lastToken = req.electionId() != null
                ? tokens.findValidToken(voter.getVoterId(), req.electionId(), now)
                : tokens.findValidTokenForVoter(voter.getVoterId(), now);
        lastToken.ifPresent(last -> {
            if (Duration.between(last.getCreatedAt(), now).compareTo(RESEND_COOLDOWN) < 0) {
                throw new IllegalStateException("Please wait before requesting a new code");
            }
        });

        // 5. Resolve channel
        String channel = req.resolvedChannel();
        if (!channel.equals("EMAIL") && !channel.equals("SMS")) {
            throw new IllegalArgumentException("Unsupported channel '" + channel + "'. Use EMAIL or SMS.");
        }
        if (channel.equals("SMS") && (voter.getPhone() == null || voter.getPhone().isBlank())) {
            throw new IllegalStateException("No phone number on file for this voter. Use EMAIL instead.");
        }

        // 6. Generate OTP and store hashed
        String otp  = generateCode6();
        String hash = sha256(otp);

        VerificationToken token = VerificationToken.builder()
                .voterId(voter.getVoterId())
                .electionId(req.electionId())
                .tokenHash(hash)
                .type("OTP")
                .channel(channel)
                .attempts(0)
                .createdAt(now)
                .expiresAt(now.plus(OTP_TTL))
                .isUsed(false)
                .build();
        tokens.save(token);

        // 7. Dispatch
        if (channel.equals("SMS")) {
            notif.sendOtpSms(voter.getPhone(), voter.getName(), otp);
        } else {
            notif.sendOtpEmail(voter.getEmail(), voter.getName(), otp);
        }

        log.info("OTP dispatched via {} for voter {} / election {}", channel,
                voter.getVoterId(), req.electionId());

        emitter.emit(
                null,
                "voter.new-otp",
                null,
                voter.getOrganization().getOrganizationId(),
                "Voter OTP",
                voter.getVoterId(),
                voter.getName(),
                "VOTER",
                "VOTER",
                "Voting",
                voter.getVoterId(),
                null,
                "Vote",
                null,
                null,
                null,
                "INFO",
                1);

        return new OtpSendResponse(token.getId(), channel);
    }

    @Override
    @Transactional
    public OtpConfirmResponse confirmOtp(OtpConfirmRequest req, HttpServletResponse response) {

        // 1. Look up voter
        Voter voter = voters.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new NoSuchElementException("Voter not found"));

        // 2. Find valid token — election-scoped or voter-level depending on flow
        var now = Instant.now();
        VerificationToken tok = (req.electionId() != null
                ? tokens.findValidToken(voter.getVoterId(), req.electionId(), now)
                : tokens.findValidTokenForVoter(voter.getVoterId(), now))
                .orElseThrow(() -> new NoSuchElementException(
                        "No active code found — please request a new one"));

        // 3. Brute-force guard
        if (tok.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many failed attempts — please request a new code");
        }

        // 4. Validate
        String hash = sha256(req.otp());
        if (!hash.equals(tok.getTokenHash())) {
            tok.setAttempts(tok.getAttempts() + 1);
            tokens.save(tok);
            int remaining = MAX_ATTEMPTS - tok.getAttempts();
            return new OtpConfirmResponse(false,
                    "Incorrect code. " + remaining + " attempt(s) remaining.", null);
        }

        // 5. Consume token
        tok.setUsed(true);
        tok.setDeleteYn(DeleteYn.Y);
        tokens.save(tok);

        // 6. Mark voter as verified for this election (only when electionId was provided).
        //    - Election-scoped OTP → mark verified on the specific ElectionVoter row.
        //    - Identity-only OTP (electionId == null) → skip here; VotingService will
        //      auto-verify when the voter casts their vote (step-up already validated
        //      by VoteController before calling the service).
        String mode = "STATION_PIN".equals(tok.getType()) ? "IN_PERSON" : "REMOTE_OTP";
        if (req.electionId() != null) {
            markVoterVerified(voter, req.electionId(), null, mode);
        }

        // 7. Issue short-lived voter JWT (carries stepUpVerifiedAt for the vote-gate)
        String voterToken = jwtService.generateVoterToken(voter.getEmail());

        ResponseCookie jwtCookie = ResponseCookie.from("voterToken", voterToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(3600)
                .build();
        response.setHeader("Set-Cookie", jwtCookie.toString());

        log.info("Voter {} verified for election {} via {}", voter.getVoterId(),
                req.electionId(), mode);

        emitter.emit(
                null,
                "voter.verified",
                null,
                voter.getOrganization().getOrganizationId(),
                "Voter OTP Verification",
                voter.getVoterId(),
                voter.getName(),
                "VOTER",
                "VOTER",
                "Voter OTP Verification",
                voter.getVoterId(),
                null,
                "Voter OTP Verification",
                null,
                null,
                null,
                "INFO",
                1);

        return new OtpConfirmResponse(true, "Identity verified successfully", voterToken);
    }

    // ── In-person flow ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public InPersonVerifyResponse verifyInPerson(UUID voterId, UUID electionId, AppUser officialId) {

        // 1. Confirm voter and election both exist
        Voter voter = voters.findById(voterId)
                .orElseThrow(() -> new NoSuchElementException("Voter not found: " + voterId));

        electionRepository.findById(electionId)
                .orElseThrow(() -> new NoSuchElementException("Election not found: " + electionId));

        // 2. Mark voter as identity-verified (official is the witness)
        markVoterVerified(voter, electionId, officialId, "IN_PERSON");

        // 3. Generate a short-lived station PIN the voter uses at the terminal
        String pin  = generateCode6();
        String hash = sha256(pin);

        var now = Instant.now();
        VerificationToken stationToken = VerificationToken.builder()
                .voterId(voterId)
                .electionId(electionId)
                .tokenHash(hash)
                .type("STATION_PIN")
                .channel("IN_PERSON")
                .attempts(0)
                .createdAt(now)
                .expiresAt(now.plus(STATION_PIN_TTL))
                .isUsed(false)
                .build();
        tokens.save(stationToken);

        log.info("Official {} verified voter {} for election {} in person. Station PIN issued.",
                officialId, voterId, electionId);

        return new InPersonVerifyResponse(true, pin,
                "Voter identity confirmed. Give this PIN to the voter — it expires in 30 minutes.");
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * Creates or updates the {@link ElectionVoter} record to reflect that this
     * voter has been identity-verified for the given election.
     *
     * <p>If a record already exists (e.g. the official already verified in-person
     * and now the voter is confirming their PIN), the method does not downgrade
     * the existing mode — it only sets fields that aren't already populated.</p>
     */
    private void markVoterVerified(Voter voter, UUID electionId, AppUser verifiedBy, String mode) {
        Optional<ElectionVoter> existing =
                evRepo.findByVoterIdAndElectionId(voter.getVoterId(), electionId);

        // verifiedBy is null for the OTP (self-service) path; only set for in-person
        UUID verifiedById = (verifiedBy != null) ? verifiedBy.getAppUserId() : null;

        if (existing.isPresent()) {
            ElectionVoter ev = existing.get();
            ev.setVerified(true);
            if (ev.getVerifiedAt() == null) {
                ev.setVerifiedAt(Timestamp.from(Instant.now()));
            }
            // Don't overwrite verifiedBy / verificationMode if already set by the
            // in-person endpoint — that record is more authoritative.
            if (ev.getVerificationMode() == null) {
                ev.setVerifiedBy(verifiedById);
                ev.setVerificationMode(mode);
            }
            evRepo.save(ev);
        } else {
            // No ElectionVoter yet — create one. VotingService will find it already
            // existing and skip its own on-the-fly creation.
            ElectionVoterId evId = new ElectionVoterId(electionId, voter.getVoterId());
            ElectionVoter ev = ElectionVoter.builder()
                    .id(evId)
                    .voter(voter)
                    .election(electionRepository.getReferenceById(electionId))
                    .hasVoted(false)
                    .verified(true)
                    .verifiedAt(Timestamp.from(Instant.now()))
                    .verifiedBy(verifiedById)
                    .verificationMode(mode)
                    .build();
            evRepo.save(ev);
        }

        emitter.emit(
                UUID.randomUUID(),
                "Voter Verification",
                Instant.now(),
                voter.getOrganization().getOrganizationId(),
                "Voter Verification",
                voter.getVoterId(),
                voter.getName(),
                "VOTER",
                "VOTER",
                "Voting",
                voter.getVoterId(),
                null,
                "Vote",
                null,
                null,
                null,
                "INFO",
                1);
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    private String generateCode6() {
        return String.valueOf(rnd.nextInt(900_000) + 100_000);
    }

    private static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
