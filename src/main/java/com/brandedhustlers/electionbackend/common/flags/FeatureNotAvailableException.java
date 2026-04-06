package com.brandedhustlers.electionbackend.common.flags;

import lombok.Getter;

/**
 * Thrown when an org attempts to use a feature not included in their tier.
 * Maps to HTTP 402 Payment Required.
 */
@Getter
public class FeatureNotAvailableException extends RuntimeException {

    private final Feature feature;
    private final Tier requiredTier;

    public FeatureNotAvailableException(Feature feature) {
        super(buildMessage(feature));
        this.feature = feature;
        this.requiredTier = Tier.minimumTierFor(feature);
    }

    private static String buildMessage(Feature feature) {
        Tier required = Tier.minimumTierFor(feature);
        return String.format(
                "Feature '%s' is not available on your current plan. " +
                "Upgrade to the %s tier or above to unlock this feature.",
                feature.name(), required.name()
        );
    }
}
