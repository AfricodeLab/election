package com.brandedhustlers.electionbackend.voting.dto;

import java.util.UUID;

public record VoteAckDTO(UUID voteId,
                         String status,
                         /**
                          * Machine-readable reason code:
                          * - "NEW_VOTE"       — Vote successfully cast
                          * - "IDEMPOTENT"     — Duplicate idempotency key; original vote returned
                          * - "ALREADY_VOTED"  — Voter already voted in this election
                          * - "ORG_MISMATCH"   — Voter's org does not match the request org
                          * - "INELIGIBLE: <reason>" — Voter does not meet eligibility requirements
                          */
                         String reason,
                         /**
                          * Human-readable message for display in voter-facing UIs.
                          * May be null for non-voter-facing paths.
                          */
                         String message
) {
    /** Convenience constructor for paths that don't need a display message. */
    public VoteAckDTO(UUID voteId, String status, String reason) {
        this(voteId, status, reason, null);
    }
}
