-- Multi-org support: junction table for user <-> organization membership
CREATE TABLE IF NOT EXISTS auth.user_organization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id     UUID NOT NULL REFERENCES auth.app_user(app_user_id),
    organization_id UUID NOT NULL REFERENCES auth.organization(organization_id),
    role_in_org     VARCHAR(50) NOT NULL,
    active_org      BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(app_user_id, organization_id)
);

CREATE INDEX idx_user_org_user ON auth.user_organization(app_user_id);
CREATE INDEX idx_user_org_org  ON auth.user_organization(organization_id);

-- Migrate existing single-FK data into the junction table
INSERT INTO auth.user_organization (id, app_user_id, organization_id, role_in_org, active_org)
SELECT gen_random_uuid(), app_user_id, organization_id, user_role, TRUE
FROM auth.app_user
WHERE organization_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Drop the old FK column from app_user
ALTER TABLE auth.app_user DROP COLUMN IF EXISTS organization_id;
