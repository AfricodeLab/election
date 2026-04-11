-- ─────────────────────────────────────────────────────────────────────────────
-- V14: Role management — fix role_name uniqueness for org-scoped custom roles
--
-- The global UNIQUE constraint on role_name prevents two different orgs from
-- creating a role with the same name (e.g. "Election Manager").
-- Replace it with two partial unique indexes:
--   1. System roles  : unique by name WHERE org_id IS NULL
--   2. Org-custom roles: unique by (name, org_id) WHERE org_id IS NOT NULL
-- ─────────────────────────────────────────────────────────────────────────────

-- Drop the global unique constraint if it exists
ALTER TABLE auth.user_roles DROP CONSTRAINT IF EXISTS user_roles_role_name_key;

-- System roles: each system role name must be globally unique
CREATE UNIQUE INDEX IF NOT EXISTS uidx_system_role_name
    ON auth.user_roles (role_name)
    WHERE org_id IS NULL;

-- Org-scoped roles: name must be unique within an org
CREATE UNIQUE INDEX IF NOT EXISTS uidx_org_role_name
    ON auth.user_roles (role_name, org_id)
    WHERE org_id IS NOT NULL;
