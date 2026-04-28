-- V23: Drop the stale unique constraint on voter.voters(created_by_id).
--
-- Background: created_by_id is a @ManyToOne FK — many voters can be created by
-- the same admin user.  A unique constraint was accidentally left on this column
-- (most likely from a prior @OneToOne mapping).  It causes every voter import to
-- fail on the second row with:
--   duplicate key value violates unique constraint "uke1sra0iss42gvdcwnsruolmg7"
--
-- The constraint is NOT needed and is schema-incorrect for a many-to-one relation.

ALTER TABLE voter.voters
    DROP CONSTRAINT IF EXISTS uke1sra0iss42gvdcwnsruolmg7;
