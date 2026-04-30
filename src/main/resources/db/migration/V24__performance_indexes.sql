-- ============================================================
-- V9: Performance indexes + drop spurious UNIQUE constraints
-- ============================================================
-- These UNIQUE constraints were generated on "created_by" /
-- "updated_by" FK columns by an ORM tool. They make no semantic
-- sense (one admin can create many elections, candidates, etc.)
-- and they cause INSERT failures as soon as the second record is
-- created by the same user. Drop them; the FK constraints remain.
-- ============================================================

-- 1. Drop spurious single-column UNIQUE constraints on *_created_by / *_updated_by
ALTER TABLE election.candidate DROP CONSTRAINT IF EXISTS uc_candidate_created_by;
ALTER TABLE election.election  DROP CONSTRAINT IF EXISTS uc_election_created_by;
ALTER TABLE election.election  DROP CONSTRAINT IF EXISTS uc_election_updated_by;
ALTER TABLE auth.app_user      DROP CONSTRAINT IF EXISTS uc_app_user_created_by;
ALTER TABLE voter.voters       DROP CONSTRAINT IF EXISTS uc_voters_created_by;
-- ALTER TABLE notif.template     DROP CONSTRAINT IF EXISTS uc_template_created_by;

-- ──────────────────────────────────────────────────────────────
-- 2. election.election — high-frequency lookup columns
-- ──────────────────────────────────────────────────────────────

-- Every admin list query filters by org; every voter query also goes through org.
CREATE INDEX IF NOT EXISTS idx_election_org_id
    ON election.election (organization_id);

-- Used by auto-close scheduler, voter live-election queries, and audit checks.
CREATE INDEX IF NOT EXISTS idx_election_status
    ON election.election (election_status);

-- Composite for the "find live elections in org" query pattern.
CREATE INDEX IF NOT EXISTS idx_election_org_status
    ON election.election (organization_id, election_status);

-- Auto-close scheduler: WHERE election_status = 'LIVE' AND election_end_time <= now
-- (existing idx_time covers start+end times but not the status filter)
CREATE INDEX IF NOT EXISTS idx_election_status_end_time
    ON election.election (election_status, election_end_time)
    WHERE election_status = 'LIVE';

-- ──────────────────────────────────────────────────────────────
-- 3. election.candidate — always queried via election_id
-- ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_candidate_election_id
    ON election.candidate (election_id);

-- ──────────────────────────────────────────────────────────────
-- 4. voter.election_voter — has_voted flag + voter_id lookups
-- ──────────────────────────────────────────────────────────────

-- PK is (election_id, voter_id). A voter_id-first lookup (e.g., "has this voter
-- voted in any election?") does a full scan without this index.
CREATE INDEX IF NOT EXISTS idx_election_voter_voter_id
    ON voter.election_voter (voter_id);

-- Used by findLiveByOrgAndNotVoted: WHERE ev.election_id = ? AND ev.voter_id = ?
-- AND ev.has_voted = true — composite covers all three predicates.
CREATE INDEX IF NOT EXISTS idx_election_voter_election_voter_voted
    ON voter.election_voter (election_id, voter_id, has_voted);

-- ──────────────────────────────────────────────────────────────
-- 5. ops.launch — org scoped queries
-- ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_launch_org_id
    ON ops.launch (org_id);

CREATE INDEX IF NOT EXISTS idx_launch_org_status
    ON ops.launch (org_id, status);

-- ──────────────────────────────────────────────────────────────
-- 6. ops.launch_election — reverse lookup by election_id
-- ──────────────────────────────────────────────────────────────
-- PK is (launch_id, election_id). Looking up which launch an election
-- belongs to (election_id first) is a full scan without this index.
CREATE INDEX IF NOT EXISTS idx_launch_election_election_id
    ON ops.launch_election (election_id);

-- ──────────────────────────────────────────────────────────────
-- 7. ops.device — queried by org and station
-- ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_device_org_id
    ON ops.device (org_id);

CREATE INDEX IF NOT EXISTS idx_device_station_id
    ON ops.device (station_id);

-- ──────────────────────────────────────────────────────────────
-- 8. ops.station — queried by org and launch
-- ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_station_org_id
    ON ops.station (org_id);

CREATE INDEX IF NOT EXISTS idx_station_launch_id
    ON ops.station (launch_id);

-- ──────────────────────────────────────────────────────────────
-- 9. voting.outbox — unpublished event polling
-- ──────────────────────────────────────────────────────────────
-- The outbox poller queries WHERE published_at IS NULL; a partial
-- index keeps it small as the backlog drains.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON voting.outbox (created_at)
    WHERE published_at IS NULL;

-- ──────────────────────────────────────────────────────────────
-- 10. voter.voters — org-scoped voter lookups
-- ──────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_voters_org_id
    ON voter.voters (org_id);
