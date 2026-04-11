-- V8: Add email verification and MFA fields to auth.app_user
-- These columns were previously managed by Hibernate ddl-auto=update.
-- This migration captures them explicitly so the schema can eventually
-- transition to ddl-auto=validate (Flyway-only ownership).

ALTER TABLE auth.app_user
    ADD COLUMN IF NOT EXISTS email_verified          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS email_verification_expiry TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mfa_enabled             BOOLEAN     NOT NULL DEFAULT FALSE;

-- Back-fill: any existing accounts registered before this migration are
-- considered verified so they are not locked out.
UPDATE auth.app_user SET email_verified = TRUE WHERE email_verified = FALSE;
