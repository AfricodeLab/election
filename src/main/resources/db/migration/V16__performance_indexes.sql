-- ─────────────────────────────────────────────────────────────────────────────
-- V16: Performance indexes for high-frequency query paths
--
-- Covers: voter search, election status filtering, refresh token lookups.
-- All indexes are created with IF NOT EXISTS so this migration is idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- Voter: org-scoped listing (most common voter query)
CREATE INDEX IF NOT EXISTS idx_voter_org_id
    ON voter.voters (org_id);

-- Voter: org + name search (used by findByOrgIDAndNameContainingIgnoreCase)
CREATE INDEX IF NOT EXISTS idx_voter_org_name
    ON voter.voters (org_id, name);

-- Election: org + status filtering (dashboard, scheduler, status changes)
CREATE INDEX IF NOT EXISTS idx_election_org_status
    ON election.election (organization_id, election_status);

-- Election: status alone (used by auto-close scheduler sweep)
CREATE INDEX IF NOT EXISTS idx_election_status
    ON election.election (election_status);

-- RefreshToken: user lookup (used on every token refresh)
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
    ON auth.refresh_token (user_id);

-- RefreshToken: expiry-based cleanup queries
CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry
    ON auth.refresh_token (expiry_date);
