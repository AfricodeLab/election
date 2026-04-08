-- ─────────────────────────────────────────────────────────────────────────────
-- V15: Drop the legacy CHECK constraint on auth.permissions.permission_type
--
-- This constraint was generated from the original generic-SaaS PermissionType
-- enum (ADD_TAX, EDIT_REPORTS, etc.) and now rejects all electoral permission
-- values introduced in V13 (CREATE_ELECTION, PUBLISH_ELECTION, etc.).
--
-- The constraint is redundant: PermissionSyncer derives valid values directly
-- from the Java PermissionType enum on every startup, so no invalid value can
-- ever reach this table. Dropping the constraint lets PermissionSyncer seed
-- the new electoral permissions without a DB violation.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE auth.permissions
    DROP CONSTRAINT IF EXISTS permissions_permission_type_check;
