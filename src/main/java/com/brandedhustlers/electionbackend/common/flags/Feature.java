package com.brandedhustlers.electionbackend.common.flags;

/**
 * Every gated capability in the platform.
 * Features are bundled into Tiers — see {@link Tier}.
 * Individual features can also be toggled per-org via OrgFeatureOverride.
 */
public enum Feature {

    // ── BASE tier ────────────────────────────────────────────────────────────
    ELECTION_MANAGEMENT,
    VOTER_MANAGEMENT,
    VOTE_CASTING,
    USER_MANAGEMENT,

    // ── INTEGRITY tier ───────────────────────────────────────────────────────
    BLOCKCHAIN_ANCHORING,
    MERKLE_VERIFICATION,
    CONFIG_ANCHORING,
    RESULTS_ANCHORING,

    // ── COMPLIANCE tier ──────────────────────────────────────────────────────
    AUDIT_TRAIL,
    TAMPER_ALERTS,
    PII_REDACTION,

    // ── ENTERPRISE tier ─────────────────────────────────────────────────────
    REAL_TIME_RESULTS,
    ADVANCED_RESILIENCE,

    // ── ANONYMIZATION tier (future) ──────────────────────────────────────────
    BLIND_BALLOT
}
