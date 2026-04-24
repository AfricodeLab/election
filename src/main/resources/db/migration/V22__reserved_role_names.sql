-- ─────────────────────────────────────────────────────────────────────────────
-- V22: Reserve system role names — prevent custom org roles from shadowing them
--
-- Root cause: an ORG_ADMIN could call POST /api/v1/roles with name="SUPER_ADMIN".
-- getAuthorities() uppercases every role name and prepends ROLE_, so the custom
-- role produced an identical ROLE_SUPER_ADMIN authority to the real system role.
-- Any user assigned that custom role would silently pass every
-- hasRole("SUPER_ADMIN") check in SecurityConfig.
--
-- This migration:
--   1. Removes any existing shadow roles and all their FK-child rows (idempotent).
--   2. Adds a CHECK constraint so the DB rejects future violations regardless of
--      whether the application-layer guard in RoleManagementService is bypassed.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
    shadow_ids BIGINT[];
BEGIN
    -- Collect IDs of org-scoped custom roles whose names collide with system roles
    SELECT ARRAY(
        SELECT role_id FROM auth.user_roles
        WHERE org_id IS NOT NULL
          AND UPPER(TRIM(role_name)) IN (
              'SUPER_ADMIN', 'ADMIN', 'ORG_ADMIN',
              'CONTRIBUTOR', 'AUDITOR', 'OBSERVER', 'VOTER'
          )
    ) INTO shadow_ids;

    IF array_length(shadow_ids, 1) IS NOT NULL THEN

        -- Remove permission assignments first (FK child of user_roles)
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'auth' AND table_name = 'auth_role_permissions'
        ) THEN
            DELETE FROM auth.auth_role_permissions WHERE role_id = ANY(shadow_ids);
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'auth_role_permissions'
        ) THEN
            DELETE FROM public.auth_role_permissions WHERE role_id = ANY(shadow_ids);
        END IF;

        -- Remove user ↔ role assignments (FK child of user_roles)
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'auth' AND table_name = 'app_user_roles'
        ) THEN
            DELETE FROM auth.app_user_roles WHERE role_id = ANY(shadow_ids);
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'app_user_roles'
        ) THEN
            DELETE FROM public.app_user_roles WHERE role_id = ANY(shadow_ids);
        END IF;

        -- Delete the shadow roles themselves
        DELETE FROM auth.user_roles WHERE role_id = ANY(shadow_ids);

        RAISE NOTICE 'V22: Removed % shadow system role(s) and all their assignments.',
            array_length(shadow_ids, 1);
    END IF;
END $$;

-- Defence-in-depth: DB-level constraint prevents future violations even if the
-- application-layer guard in RoleManagementService.createRole() is bypassed
-- (direct DB access, a future code path that forgets to check, etc.).
-- The comparison is case-insensitive (UPPER + TRIM) to match getAuthorities().
ALTER TABLE auth.user_roles
    ADD CONSTRAINT chk_no_system_role_name_collision
    CHECK (
        org_id IS NULL   -- system roles (orgId = null) are exempt
        OR UPPER(TRIM(role_name)) NOT IN (
            'SUPER_ADMIN', 'ADMIN', 'ORG_ADMIN',
            'CONTRIBUTOR', 'AUDITOR', 'OBSERVER', 'VOTER'
        )
    );
