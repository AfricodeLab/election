-- ─────────────────────────────────────────────────────────────────────────────
-- V20: Add must_change_password flag to app_user.
--
--  When an admin provisions a new account the column is set to TRUE so that
--  the user is forced to change their temporary credentials on first login.
--  Self-registered users always get FALSE (they chose their own password).
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE auth.app_user
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
