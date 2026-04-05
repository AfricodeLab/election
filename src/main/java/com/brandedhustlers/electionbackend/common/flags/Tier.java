package com.brandedhustlers.electionbackend.common.flags;

import java.util.EnumSet;
import java.util.Set;

/**
 * Subscription tiers. Each tier is additive — it includes all features
 * from lower tiers plus its own.
 */
public enum Tier {

    BASE(
            Feature.ELECTION_MANAGEMENT,
            Feature.VOTER_MANAGEMENT,
            Feature.VOTE_CASTING,
            Feature.USER_MANAGEMENT
    ),

    INTEGRITY(
            // includes BASE +
            Feature.ELECTION_MANAGEMENT,
            Feature.VOTER_MANAGEMENT,
            Feature.VOTE_CASTING,
            Feature.USER_MANAGEMENT,
            Feature.BLOCKCHAIN_ANCHORING,
            Feature.MERKLE_VERIFICATION,
            Feature.CONFIG_ANCHORING,
            Feature.RESULTS_ANCHORING
    ),

    COMPLIANCE(
            // includes INTEGRITY +
            Feature.ELECTION_MANAGEMENT,
            Feature.VOTER_MANAGEMENT,
            Feature.VOTE_CASTING,
            Feature.USER_MANAGEMENT,
            Feature.BLOCKCHAIN_ANCHORING,
            Feature.MERKLE_VERIFICATION,
            Feature.CONFIG_ANCHORING,
            Feature.RESULTS_ANCHORING,
            Feature.AUDIT_TRAIL,
            Feature.TAMPER_ALERTS,
            Feature.PII_REDACTION
    ),

    ENTERPRISE(
            // includes COMPLIANCE +
            Feature.ELECTION_MANAGEMENT,
            Feature.VOTER_MANAGEMENT,
            Feature.VOTE_CASTING,
            Feature.USER_MANAGEMENT,
            Feature.BLOCKCHAIN_ANCHORING,
            Feature.MERKLE_VERIFICATION,
            Feature.CONFIG_ANCHORING,
            Feature.RESULTS_ANCHORING,
            Feature.AUDIT_TRAIL,
            Feature.TAMPER_ALERTS,
            Feature.PII_REDACTION,
            Feature.REAL_TIME_RESULTS,
            Feature.ADVANCED_RESILIENCE
    ),

    ANONYMIZATION(
            // includes ENTERPRISE +
            Feature.ELECTION_MANAGEMENT,
            Feature.VOTER_MANAGEMENT,
            Feature.VOTE_CASTING,
            Feature.USER_MANAGEMENT,
            Feature.BLOCKCHAIN_ANCHORING,
            Feature.MERKLE_VERIFICATION,
            Feature.CONFIG_ANCHORING,
            Feature.RESULTS_ANCHORING,
            Feature.AUDIT_TRAIL,
            Feature.TAMPER_ALERTS,
            Feature.PII_REDACTION,
            Feature.REAL_TIME_RESULTS,
            Feature.ADVANCED_RESILIENCE,
            Feature.BLIND_BALLOT
    );

    private final Set<Feature> features;

    Tier(Feature... features) {
        this.features = EnumSet.copyOf(Set.of(features));
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public boolean includes(Feature feature) {
        return features.contains(feature);
    }

    /** The minimum tier required to access a given feature. */
    public static Tier minimumTierFor(Feature feature) {
        for (Tier tier : values()) {
            if (tier.includes(feature)) return tier;
        }
        throw new IllegalArgumentException("Feature not assigned to any tier: " + feature);
    }
}
