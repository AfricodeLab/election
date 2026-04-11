package com.brandedhustlers.electionbackend.common.flags;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/features")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    /** List all tiers and the features each includes. */
    @GetMapping("/tiers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> listTiers() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Tier tier : Tier.values()) {
            result.put(tier.name(), tier.getFeatures().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList());
        }
        return ResponseEntity.ok(result);
    }

    /** Get the full feature status for an org (tier + overrides resolved). */
    @GetMapping("/org/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrgFeatureStatusResponse> getOrgFeatures(@PathVariable UUID orgId) {
        Tier tier = featureFlagService.getTier(orgId);
        Map<Feature, Boolean> status = featureFlagService.getFeatureStatus(orgId);
        return ResponseEntity.ok(new OrgFeatureStatusResponse(orgId, tier, status));
    }

    /** Upgrade or downgrade an org's tier. */
    @PatchMapping("/org/{orgId}/tier")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> setTier(@PathVariable UUID orgId,
                                        @RequestBody SetTierRequest req) {
        featureFlagService.setTier(orgId, req.tier());
        return ResponseEntity.noContent().build();
    }

    /** Force-enable or force-disable a specific feature for an org (override). */
    @PatchMapping("/org/{orgId}/override")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> setOverride(@PathVariable UUID orgId,
                                            @RequestBody SetOverrideRequest req) {
        featureFlagService.setOverride(orgId, req.feature(), req.enabled(), req.reason());
        return ResponseEntity.noContent().build();
    }

    /** Remove a feature override, reverting to tier-based resolution. */
    @DeleteMapping("/org/{orgId}/override/{feature}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> removeOverride(@PathVariable UUID orgId,
                                               @PathVariable Feature feature) {
        featureFlagService.removeOverride(orgId, feature);
        return ResponseEntity.noContent().build();
    }

    /** Manually evict the feature flag cache for an org (useful after manual DB changes). */
    @PostMapping("/org/{orgId}/cache/evict")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> evictCache(@PathVariable UUID orgId) {
        featureFlagService.evictCache(orgId);
        return ResponseEntity.noContent().build();
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record SetTierRequest(Tier tier) {}

    public record SetOverrideRequest(Feature feature, boolean enabled, String reason) {}

    public record OrgFeatureStatusResponse(
            UUID orgId,
            Tier currentTier,
            Map<Feature, Boolean> features
    ) {}
}
