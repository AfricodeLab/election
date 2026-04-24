package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface IVotingService {
    /**
     * @param stepUpVerified true when the caller (VoteController) has already validated
     *                       the voter's step-up JWT claim. When true, the service will
     *                       auto-verify unverified ElectionVoter records rather than
     *                       rejecting them, supporting the identity-only OTP flow where
     *                       verification is not pre-scoped to a specific election.
     */
    VoteAckDTO cast(UUID orgId, UUID userId, CastVoteCmd cmd, HttpServletRequest httpServletRequest, boolean stepUpVerified);
}
