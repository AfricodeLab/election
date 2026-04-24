-- ─────────────────────────────────────────────────────────────────────────────
-- V18: Materialise V6 (custom election types + presets) and add every column
--      that the current entity model expects but that was never migrated.
--
--  All DDL uses IF NOT EXISTS / DO-block guards so this migration is safe
--  to run on both fresh databases and existing ones that already have some
--  of the objects (created by old Hibernate auto-DDL).
--
--  Objects created / altered:
--    1. election.election_type    (new table)
--    2. Seed 7 system election types
--    3. election.election_preset  (new table)
--    4. election.election         ADD COLUMNS:
--         election_type_id, preset_id, review_required, review_reason,
--         eligibility_policy, policy_version, launch_launch_id
-- ─────────────────────────────────────────────────────────────────────────────


-- ── 1.  election.election_type ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS election.election_type (
    type_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    is_system       BOOLEAN      NOT NULL DEFAULT FALSE,
    organization_id UUID         REFERENCES auth.organization (organization_id) ON DELETE CASCADE,
    created_by      UUID         REFERENCES auth.app_user (app_user_id) ON DELETE SET NULL,
    delete_yn       VARCHAR(1)   NOT NULL DEFAULT 'N',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_election_type PRIMARY KEY (type_id)
);

-- Unique name per org  (system types have organization_id = NULL)
-- Two partial indexes handle this without NULLS NOT DISTINCT (PG 15+ only):
CREATE UNIQUE INDEX IF NOT EXISTS uidx_election_type_name_system
    ON election.election_type (name)
    WHERE organization_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uidx_election_type_name_org
    ON election.election_type (name, organization_id)
    WHERE organization_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_election_type_org
    ON election.election_type (organization_id)
    WHERE delete_yn = 'N';


-- ── 2.  Seed system election types ────────────────────────────────────────────

INSERT INTO election.election_type (name, description, is_system, organization_id, delete_yn)
VALUES
    ('GUBERNATORIAL',    'Gubernatorial election',            TRUE, NULL, 'N'),
    ('PRESIDENTIAL',     'Presidential election',             TRUE, NULL, 'N'),
    ('SENATORIAL',       'Senatorial election',               TRUE, NULL, 'N'),
    ('HOUSE_OF_REPS',    'House of Representatives election', TRUE, NULL, 'N'),
    ('LOCAL_GOVERNMENT', 'Local government election',         TRUE, NULL, 'N'),
    ('STUDENT_UNION',    'Student union election',            TRUE, NULL, 'N'),
    ('OTHER',            'Other / unclassified election',     TRUE, NULL, 'N')
ON CONFLICT DO NOTHING;


-- ── 3.  election.election_preset ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS election.election_preset (
    preset_id                        UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                             VARCHAR(150) NOT NULL,
    description                      TEXT,
    type_id                          UUID         NOT NULL
                                         REFERENCES election.election_type (type_id) ON DELETE RESTRICT,
    organization_id                  UUID         NOT NULL
                                         REFERENCES auth.organization (organization_id) ON DELETE CASCADE,
    default_eligibility_policy       JSONB,
    default_expected_voter_count     BIGINT,
    default_should_end_automatically BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by                       UUID         REFERENCES auth.app_user (app_user_id) ON DELETE SET NULL,
    delete_yn                        VARCHAR(1)   NOT NULL DEFAULT 'N',
    created_at                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_election_preset PRIMARY KEY (preset_id)
);

CREATE INDEX IF NOT EXISTS idx_election_preset_org
    ON election.election_preset (organization_id)
    WHERE delete_yn = 'N';

CREATE INDEX IF NOT EXISTS idx_election_preset_type
    ON election.election_preset (type_id);


-- ── 4.  Add missing columns to election.election ─────────────────────────────
--
--  Column-name notes:
--    election_type_id  – explicit @JoinColumn(name="election_type_id")
--    preset_id         – explicit @JoinColumn(name="preset_id")
--    launch_launch_id  – bare @ManyToOne Launch launch; Spring implicit naming
--                        produces {field}_{pk_column} = "launch_launch_id"
--    review_required   – @Column(name="review_required")
--    review_reason     – @Column(name="review_reason")
--    eligibility_policy– @Column(name="eligibility_policy", columnDefinition="jsonb")
--    policy_version    – @Column(name="policy_version"), default 1

ALTER TABLE election.election
    ADD COLUMN IF NOT EXISTS election_type_id   UUID        REFERENCES election.election_type   (type_id)   ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS preset_id          UUID        REFERENCES election.election_preset  (preset_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS launch_launch_id   UUID        REFERENCES ops.launch               (launch_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS review_required    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS review_reason      TEXT,
    ADD COLUMN IF NOT EXISTS eligibility_policy JSONB,
    ADD COLUMN IF NOT EXISTS policy_version     INTEGER     NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_election_election_type_id
    ON election.election (election_type_id);

CREATE INDEX IF NOT EXISTS idx_election_preset_id
    ON election.election (preset_id);
