-- ─────────────────────────────────────────────────────────────────────────────
-- V17: Create auth.refresh_token table
--
-- The table was previously managed by Hibernate auto-DDL and was never part
-- of a versioned migration.  This script creates it properly so that fresh
-- deployments work, and is safe to run on databases where the table already
-- exists (everything uses IF NOT EXISTS).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth.refresh_token (
    id          UUID                        NOT NULL,
    token       VARCHAR(255)                NOT NULL,
    user_id     UUID,
    expiry_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uc_refresh_token_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES auth.app_user (app_user_id)
        ON DELETE CASCADE
);

-- Indexes (safe to run even when V16 already created them on an existing DB)
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id
    ON auth.refresh_token (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry
    ON auth.refresh_token (expiry_date);
