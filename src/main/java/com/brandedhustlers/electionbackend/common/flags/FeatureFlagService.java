package com.brandedhustlers.electionbackend.common.flags;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface FeatureFlagService {

    /** Returns true if the given feature is active for the org. */
    boolean isEnabled(UUID orgId, Feature feature);

    /** Returns the full feature status map for an org (feature → enabled). */
    Map<Feature, Boolean> getFeatureStatus(UUID orgId);

    /** Returns the org's current tier. */
    Tier getTier(UUID orgId);

    /** Change an org's tier. Invalidates cache. */
    void setTier(UUID orgId, Tier tier);

    /** Force-enable or force-disable a specific feature for an org. */
    void setOverride(UUID orgId, Feature feature, boolean enabled, String reason);

    /** Remove a feature override, reverting to tier-based resolution. */
    void removeOverride(UUID orgId, Feature feature);

    /** Evict all cached flag state for an org. */
    void evictCache(UUID orgId);

    /** Returns all features included in a tier (for documentation/UI). */
    Set<Feature> featuresForTier(Tier tier);
}
