-- ─────────────────────────────────────────────────────────────────────────────
-- V19: Create all remaining tables and add all missing columns so that
--      Hibernate ddl-auto=validate passes cleanly on a fresh deployment.
--
--  New schemas:  audit, chain
--
--  New tables:
--    audit.domain_event
--    chain.anchor_batch
--    chain.anchor_cursor
--    voting.vote_archive
--    voting.tampering_log
--    voting.election_results_anchor
--    voting.election_config_anchor
--    voting.election_config_snapshot
--
--  Altered tables:
--    voter.voters              + attributes, attributes_source, attributes_updated_at
--    voter.verification_token  + channel, is_used, delete_yn
--    voter.election_voter      + voted_at
--    voting.outbox             + vote_hash, publish_status, failed_validation_at,
--                                anchor_pending, anchored, batch_id, anchored_at,
--                                election_id
--                              ~ payload column: OID → TEXT  (entity now uses JSON)
--
--  All DDL is guarded with IF NOT EXISTS / DO-block checks for idempotency.
-- ─────────────────────────────────────────────────────────────────────────────


-- ── 0.  Schemas ───────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS chain;


-- ── 1.  audit.domain_event ────────────────────────────────────────────────────
--  Maps to DomainEvent.java  (@Table schema="audit", name="domain_event")

CREATE TABLE IF NOT EXISTS audit.domain_event (
    audit_id       UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_id       UUID         NOT NULL,
    org_id         UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    action_label   VARCHAR(255) NOT NULL,
    actor_id       UUID,
    actor_name     VARCHAR(255),
    actor_role     VARCHAR(255),
    actor_type     VARCHAR(50),
    target_type    VARCHAR(100),
    target_id      UUID,
    target_label   VARCHAR(255),
    severity       VARCHAR(20)  NOT NULL,
    source         VARCHAR(50),
    correlation_id VARCHAR(255),
    ip_hash        VARCHAR(255),
    ua_hash        VARCHAR(255),

    CONSTRAINT pk_domain_event       PRIMARY KEY (audit_id),
    CONSTRAINT uc_domain_event_event UNIQUE      (event_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_org_time
    ON audit.domain_event (org_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_org_evt_type_time
    ON audit.domain_event (org_id, event_type, occurred_at DESC);


-- ── 2.  chain.anchor_batch ────────────────────────────────────────────────────
--  Maps to Batch.java  (@Table schema="chain", name="anchor_batch")
--  Spring implicit naming: batchId→batch_id, electionId→election_id, etc.

CREATE TABLE IF NOT EXISTS chain.anchor_batch (
    batch_id    UUID         NOT NULL,
    election_id UUID,
    org_id      UUID,
    merkle_root VARCHAR(255),
    from_ts     TIMESTAMP WITHOUT TIME ZONE,
    to_ts       TIMESTAMP WITHOUT TIME ZONE,
    status      VARCHAR(255),
    tx_id       VARCHAR(255),
    anchored_at TIMESTAMP WITHOUT TIME ZONE,
    created_at  TIMESTAMP WITHOUT TIME ZONE,
    vote_count  INTEGER,
    chain_tx_id VARCHAR(255),

    CONSTRAINT pk_anchor_batch PRIMARY KEY (batch_id)
);

CREATE INDEX IF NOT EXISTS idx_anchor_batch_election
    ON chain.anchor_batch (election_id);

CREATE INDEX IF NOT EXISTS idx_anchor_batch_status
    ON chain.anchor_batch (status);


-- ── 3.  chain.anchor_cursor ───────────────────────────────────────────────────
--  Maps to AnchorCursor.java  (@Table schema="chain")
--  Table name defaults to class name: anchor_cursor
--  Spring naming: electionId→election_id, lastAnchoredAt→last_anchored_at

CREATE TABLE IF NOT EXISTS chain.anchor_cursor (
    election_id      UUID NOT NULL,
    last_anchored_at TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT pk_anchor_cursor PRIMARY KEY (election_id)
);


-- ── 4.  voting.vote_archive ───────────────────────────────────────────────────
--  Maps to VoteArchive.java  (@Table schema="voting", name="vote_archive")

CREATE TABLE IF NOT EXISTS voting.vote_archive (
    vote_id      UUID         NOT NULL,
    election_id  UUID         NOT NULL,
    candidate_id UUID         NOT NULL,
    voter_id     UUID         NOT NULL,
    org_id       UUID         NOT NULL,
    dedupe_key   VARCHAR(255),
    cast_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    vote_hash    VARCHAR(255) NOT NULL,
    archived_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT pk_vote_archive PRIMARY KEY (vote_id)
);

CREATE INDEX IF NOT EXISTS idx_vote_archive_election
    ON voting.vote_archive (election_id);

CREATE INDEX IF NOT EXISTS idx_vote_archive_hash
    ON voting.vote_archive (vote_hash);


-- ── 5.  voting.tampering_log ──────────────────────────────────────────────────
--  Maps to TamperingLog.java  (@Table schema="voting", name="tampering_log")

CREATE TABLE IF NOT EXISTS voting.tampering_log (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    vote_id       UUID        NOT NULL,
    election_id   UUID        NOT NULL,
    org_id        UUID        NOT NULL,
    incident_type VARCHAR(50) NOT NULL,
    original_hash VARCHAR(64) NOT NULL,
    tampered_hash VARCHAR(64),
    archive_hash  VARCHAR(64),
    detected_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resolved      BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_tampering_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_tampering_election
    ON voting.tampering_log (election_id);

CREATE INDEX IF NOT EXISTS idx_tampering_detected
    ON voting.tampering_log (detected_at);

CREATE INDEX IF NOT EXISTS idx_tampering_vote
    ON voting.tampering_log (vote_id);


-- ── 6.  voting.election_results_anchor ───────────────────────────────────────
--  Maps to ElectionResultsAnchor.java  (@Table schema="voting")

CREATE TABLE IF NOT EXISTS voting.election_results_anchor (
    election_id         UUID        NOT NULL,
    results_json        TEXT        NOT NULL,
    results_hash        VARCHAR(64) NOT NULL,
    excluded_vote_count INTEGER     NOT NULL DEFAULT 0,
    chain_tx_id         VARCHAR(255),
    released_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    released_by         UUID        NOT NULL,
    force_released      BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_election_results_anchor PRIMARY KEY (election_id)
);


-- ── 7.  voting.election_config_anchor ────────────────────────────────────────
--  Maps to ElectionConfigAnchor.java  (@Table schema="voting")

CREATE TABLE IF NOT EXISTS voting.election_config_anchor (
    election_id UUID        NOT NULL,
    config_json TEXT        NOT NULL,
    config_hash VARCHAR(64) NOT NULL,
    chain_tx_id VARCHAR(255),
    anchored_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,

    CONSTRAINT pk_election_config_anchor PRIMARY KEY (election_id)
);


-- ── 8.  voting.election_config_snapshot ──────────────────────────────────────
--  Maps to ElectionConfigSnapshot.java  (@Table schema="voting")

CREATE TABLE IF NOT EXISTS voting.election_config_snapshot (
    snapshot_id     UUID        NOT NULL DEFAULT gen_random_uuid(),
    election_id     UUID        NOT NULL,
    snapshot_type   VARCHAR(20) NOT NULL,
    candidates_json TEXT        NOT NULL,
    config_hash     VARCHAR(64) NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_by      UUID,
    change_reason   TEXT,

    CONSTRAINT pk_election_config_snapshot PRIMARY KEY (snapshot_id)
);

CREATE INDEX IF NOT EXISTS idx_config_snapshot_election
    ON voting.election_config_snapshot (election_id, created_at);


-- ── 9.  voter.voters — new columns ───────────────────────────────────────────
--  Voter.java added: attributes (JSONB), attributes_source, attributes_updated_at

ALTER TABLE voter.voters
    ADD COLUMN IF NOT EXISTS attributes            JSONB,
    ADD COLUMN IF NOT EXISTS attributes_source     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attributes_updated_at TIMESTAMP WITHOUT TIME ZONE;


-- ── 10. voter.verification_token — new columns ───────────────────────────────
--  VerificationToken.java added: channel, isUsed (→is_used), deleteYn (→delete_yn)

ALTER TABLE voter.verification_token
    ADD COLUMN IF NOT EXISTS channel   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_used   BOOLEAN    NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS delete_yn VARCHAR(1);


-- ── 11. voter.election_voter — new column ────────────────────────────────────
--  ElectionVoter.java added: votedAt (→voted_at)

ALTER TABLE voter.election_voter
    ADD COLUMN IF NOT EXISTS voted_at TIMESTAMP WITHOUT TIME ZONE;


-- ── 12. voting.outbox — new columns ──────────────────────────────────────────
--  OutBox.java added: voteHash, publishStatus, failedValidationAt,
--                     anchorPending, anchored, batchId, anchoredAt, electionId

ALTER TABLE voting.outbox
    ADD COLUMN IF NOT EXISTS vote_hash            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS publish_status       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS failed_validation_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS anchor_pending       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS anchored             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS batch_id             UUID,
    ADD COLUMN IF NOT EXISTS anchored_at          TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS election_id          UUID;


-- ── 13. voting.outbox — fix payload column type ──────────────────────────────
--  V5 created payload as OID; OutBox.java now uses @JdbcTypeCode(SqlTypes.JSON)
--  which requires TEXT.  On a fresh DB the table is empty so we drop-and-add.
--  On an existing DB with data the OID large-object refs are discarded
--  (acceptable: outbox is transient, not a source of truth).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM   information_schema.columns
        WHERE  table_schema = 'voting'
        AND    table_name   = 'outbox'
        AND    column_name  = 'payload'
        AND    data_type    = 'oid'
    ) THEN
        ALTER TABLE voting.outbox DROP COLUMN payload;
        ALTER TABLE voting.outbox ADD  COLUMN payload TEXT NOT NULL DEFAULT '';
        ALTER TABLE voting.outbox ALTER COLUMN payload DROP DEFAULT;
    END IF;
END $$;
