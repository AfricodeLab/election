-- Feature flag support: tier column on organization + per-org overrides table.

-- Add subscription tier to organization (defaults to BASE for all existing orgs)
ALTER TABLE auth.organization
    ADD COLUMN IF NOT EXISTS tier VARCHAR(32) NOT NULL DEFAULT 'BASE';

-- Per-org feature overrides (custom deals, trials, kill-switches)
CREATE TABLE IF NOT EXISTS auth.org_feature_override (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES auth.organization(organization_id) ON DELETE CASCADE,
    feature    VARCHAR(64)  NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_feature UNIQUE (org_id, feature)
);

CREATE INDEX IF NOT EXISTS idx_org_feature_override_org_id ON auth.org_feature_override(org_id);
