package com.brandedhustlers.electionbackend.voting.dto;

import java.util.UUID;

public record VoteAckDTO(UUID voteId,
                         String status,
                         /**
                          * Reason code:
                          * - "NEW_VOTE" - Vote successfully cast
                          * - "IDEMPOTENT" - Vote already recorded with this idempotency key
                          * - "ALREADY_VOTED" - Voter already voted in this election
                          * - "INELIGIBLE: <reason>" - Voter does not meet eligibility requirements
                          */
                         String reason
) {

}