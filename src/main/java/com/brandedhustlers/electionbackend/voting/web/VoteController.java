package com.brandedhustlers.electionbackend.voting.web;

import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import com.brandedhustlers.electionbackend.voting.service.VotingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/votes")
public class VoteController {
    private final VotingService votingService;
    private final HttpServletRequest httpServletRequest;
    private final JWTService jwtService;

    public VoteController(VotingService votingService,
                          HttpServletRequest httpServletRequest,
                          JWTService jwtService) {
        this.votingService = votingService;
        this.httpServletRequest = httpServletRequest;
        this.jwtService = jwtService;
    }

    @PostMapping("/cast")
    public ResponseEntity<VoteAckDTO> cast(@RequestHeader("X-Org-Id") UUID orgId,
                                           @RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idem,
                                           @RequestBody CastVoteCmd body) {
        CastVoteCmd cmd = new CastVoteCmd(body.electionId(), body.candidateId(), body.voterToken(), body.deviceId(), idem);
        return ResponseEntity.ok(votingService.cast(orgId, userId, cmd));
    }
}