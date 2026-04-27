package com.brandedhustlers.electionbackend.voting.web;

import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.config.security.VoterAuthToken;
import com.brandedhustlers.electionbackend.common.events.AuditEmitter;
import com.brandedhustlers.electionbackend.voter.service.VerificationService;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.service.VotingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/votes")
public class VoteController {
    private final VotingService votingService;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;
    private final AuditEmitter auditEmitter;

    public VoteController(VotingService votingService,
            HttpServletRequest httpServletRequest,
            JWTService jwtService,
            AuditEmitter auditEmitter) {
        this.votingService = votingService;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
        this.auditEmitter = auditEmitter;
    }

    private static final Duration STEP_UP_WINDOW = Duration.ofMinutes(15);

    private void requireFreshStepUp(HttpSession session) {
        var ts = (Instant) session.getAttribute(VerificationService.SESSION_KEY);
        if (ts == null) {
            throw new SecurityException("VOTE-401 step-up required");
        }

        if (Duration.between(ts, Instant.now()).compareTo(STEP_UP_WINDOW) > 0) {
            throw new SecurityException("VOTE-401 step-up expired");
        }
    }

    /*
     * @PostMapping("/cast")
     * public ResponseEntity<VoteAckDTO> cast(@RequestBody CastVoteCmd body,
     * HttpSession session){
     * requireFreshStepUp(session);
     * UUID orgId = (UUID)
     * session.getAttribute(VerificationService.SESSION_KEY_ORG);
     * UUID voterId = (UUID)
     * session.getAttribute(VerificationService.SESSION_KEY_VOTER);
     * if (orgId == null || voterId == null){
     * throw new SecurityException("VOTE-401 not logged in as voter");
     * }
     * return ResponseEntity.ok(votingService.cast(orgId, voterId, body));
     * }
     */

    @PostMapping("/cast")
    public ResponseEntity<VoteAckDTO> cast(@Valid @RequestBody CastVoteCmd body) {
        var auth = (VoterAuthToken) SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(401).build();
        }

        Long stepUp = auth.getStepUpVerifiedAt();
        if (stepUp == null || (System.currentTimeMillis() - stepUp) > STEP_UP_WINDOW.toMillis()) {
            auditEmitter.emit(null, "voting.step-up.expired", null,
                    auth.getVoterOrganizationId(),
                    stepUp == null ? "Vote rejected: no step-up claim in token" : "Vote rejected: step-up verification expired",
                    auth.getVoterId(), null, "VOTER", "VOTER",
                    "Voting", body.electionId(), null, "Vote", null, null, null, "WARN", 1);
            return ResponseEntity.status(401)
                    .body(new VoteAckDTO(null, "REJECTED", "Step up required, please verify again to vote."));
        }

        // stepUpVerified=true: step-up JWT claim was already validated above
        VoteAckDTO ack = votingService.cast(auth.getVoterOrganizationId(), auth.getVoterId(), body, httpServletRequest, true);

        HttpStatus httpStatus = switch (ack.reason()) {
            case "NEW_VOTE" -> HttpStatus.CREATED;
            case "IDEMPOTENT" -> HttpStatus.OK;
            case "ALREADY_VOTED" -> HttpStatus.CONFLICT;
            default -> ack.reason().startsWith("INELIGIBLE")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
        };

        return new ResponseEntity<>(ack, httpStatus);
    }
}