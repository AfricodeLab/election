-- Pre-computed eligibility snapshot table.
-- Populated when a launch transitions to ACTIVE so the email path
-- can read eligible voters from the database instead of evaluating
-- policies in-memory for every voter × election pair.

CREATE TABLE IF NOT EXISTS voter.eligible_voter_election (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voter_id      UUID        NOT NULL,
    election_id   UUID        NOT NULL,
    launch_id     UUID        NOT NULL,
    computed_at   TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT uq_eligible_voter_election UNIQUE (voter_id, election_id),

    CONSTRAINT fk_eve_voter
        FOREIGN KEY (voter_id) REFERENCES voter.voters (voter_id),
    CONSTRAINT fk_eve_election
        FOREIGN KEY (election_id) REFERENCES election.election (election_id),
    CONSTRAINT fk_eve_launch
        FOREIGN KEY (launch_id) REFERENCES ops.launch (launch_id)
);

CREATE INDEX idx_eve_launch_id  ON voter.eligible_voter_election (launch_id);
CREATE INDEX idx_eve_voter_id   ON voter.eligible_voter_election (voter_id);
