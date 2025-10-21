package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.voting.dto.CastVoteCmd;
import com.brandedhustlers.electionbackend.voting.dto.VoteAckDTO;

import java.util.UUID;

public interface IVotingService {
    VoteAckDTO cast(UUID orgId, UUID userId, CastVoteCmd cmd);
}
