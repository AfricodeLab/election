-- ─────────────────────────────────────────────────────────────────────────────
-- V13: Election-specific permission model
--
-- 1. Add org_id to user_roles so custom org roles are tenant-scoped.
--    System roles (seeded by BH) keep org_id = NULL.
--
-- 2. Purge the old generic-SaaS permission rows (ADD_TAX, EDIT_REPORTS, etc.)
--    and their role assignments. PermissionSyncer will re-seed on next startup
--    with the new electoral PermissionType enum values.
-- ─────────────────────────────────────────────────────────────────────────────

-- Gap 2: Org-scope custom roles ───────────────────────────────────────────────
ALTER TABLE auth.user_roles
    ADD COLUMN IF NOT EXISTS org_id UUID;

-- Gap 1: Purge stale generic permissions ──────────────────────────────────────
-- Remove role-permission assignments first (FK child), then the permissions.
-- The join table Hibernate creates for Role.rolePermissions is auth_role_permissions.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'auth' AND table_name = 'auth_role_permissions'
    ) THEN
        DELETE FROM auth.auth_role_permissions;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'auth_role_permissions'
    ) THEN
        DELETE FROM public.auth_role_permissions;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'auth' AND table_name = 'permissions'
    ) THEN
        DELETE FROM auth.permissions;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'permissions'
    ) THEN
        DELETE FROM public.permissions;
    END IF;
END $$;
