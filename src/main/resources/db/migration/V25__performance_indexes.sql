-- ============================================================
-- V9: Performance indexes + schema corrections
-- Applied: 2025-05-01
-- ============================================================

-- ── voter.voters ──────────────────────────────────────────────────────────────
-- Every org-scoped voter query filters on org_id; without this index
-- Postgres does a full sequential scan of the voters table.
CREATE INDEX IF NOT EXISTS idx_voters_org_id
    ON voter.voters (org_id);

-- Composite covering index for the most common query pattern:
-- "active voters in org" (findByOrgId adds deleteYn = 'N')
CREATE INDEX IF NOT EXISTS idx_voters_org_delete
    ON voter.voters (org_id, delete_yn);

-- ── election.election ─────────────────────────────────────────────────────────
-- findByOrganizationId, findLiveByOrg*, findElectionsReadyToClose all filter
-- by organization_id and/or election_status.
CREATE INDEX IF NOT EXISTS idx_election_org_id
    ON election.election (organization_id);

CREATE INDEX IF NOT EXISTS idx_election_status
    ON election.election (election_status);

-- Composite for the common (org + status) filter pattern
CREATE INDEX IF NOT EXISTS idx_election_org_status
    ON election.election (organization_id, election_status);

-- ── voter.election_voter ──────────────────────────────────────────────────────
-- PK is (election_id, voter_id). The NOT EXISTS subquery in
-- findLiveByOrgAndNotVoted searches by voter_id; without this index
-- Postgres scans the full election_voter table for every voter lookup.
CREATE INDEX IF NOT EXISTS idx_ev_voter_id
    ON voter.election_voter (voter_id);

-- Covering index for the "has this voter already voted?" check
CREATE INDEX IF NOT EXISTS idx_ev_voter_voted
    ON voter.election_voter (voter_id, has_voted);

-- ── ops.launch ────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_launch_org_id
    ON ops.launch (org_id);

-- ── ops.station ───────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_station_org_launch
    ON ops.station (org_id, launch_id);

-- ── ops.device ────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_device_org_status
    ON ops.device (org_id, status);

-- ── voting.outbox ─────────────────────────────────────────────────────────────
-- Partial index covering only the pending (unpublished) rows.
-- OutBoxService.publishPending() scans WHERE published_at IS NULL every 10 s.
-- As published rows accumulate this table grows; the partial index keeps the
-- working set small so the scheduler stays fast throughout the election lifetime.
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON voting.outbox (created_at)
    WHERE published_at IS NULL;

-- ── voter.eligible_voter_election ─────────────────────────────────────────────
-- Fix: migration V8 defined launch_id as NOT NULL but standalone elections
-- (brought live via POST /election/live/{id}) set launch_id = null.
-- This constraint violation would cause a runtime error at go-live.
ALTER TABLE voter.eligible_voter_election
    ALTER COLUMN launch_id DROP NOT NULL;
