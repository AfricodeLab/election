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

-- RefreshToken: indexes are created only when the table exists.
-- On a fresh deploy the table is created in V17; on an existing DB it is
-- already present, so the DO block runs the CREATE INDEX statements.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE  table_schema = 'auth'
        AND    table_name   = 'refresh_token'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
            ON auth.refresh_token (user_id);

        CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry
            ON auth.refresh_token (expiry_date);
    END IF;
END $$;
