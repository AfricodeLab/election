-- ─────────────────────────────────────────────────────────────────────────────
-- V21 — Voter verification
--
-- 1. Scope OTP/station-pin tokens to a specific election.
-- 2. Record identity-verification state on each voter-election row.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── verification_token: tie each token to one election ───────────────────────
ALTER TABLE voter.verification_token
    ADD COLUMN IF NOT EXISTS election_id UUID;

-- ── election_voter: track verified status per voter per election ──────────────
ALTER TABLE voter.election_voter
    ADD COLUMN IF NOT EXISTS verified          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verified_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verified_by       UUID,        -- NULL = self-service OTP, UUID = official
    ADD COLUMN IF NOT EXISTS verification_mode VARCHAR(20); -- REMOTE_OTP | IN_PERSON

-- Index: fast lookup of unverified voters during vote-gate checks
CREATE INDEX IF NOT EXISTS idx_election_voter_verified
    ON voter.election_voter (election_id, verified);
