-- V10: Add password-reset token fields to auth.app_user
ALTER TABLE auth.app_user
    ADD COLUMN IF NOT EXISTS password_reset_token  VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS password_reset_expiry TIMESTAMPTZ;
