package com.brandedhustlers.electionbackend.common.flags;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private final OrganizationRepository organizationRepository;
    private final OrgFeatureOverrideRepository overrideRepository;

    // Cache name matches the Redis cache config (5-min TTL from RedisConfig)
    private static final String CACHE = "featureFlags";

    @Override
    @Cacheable(value = CACHE, key = "#orgId + ':' + #feature.name()")
    public boolean isEnabled(UUID orgId, Feature feature) {
        // 1. Check per-org override first
        Optional<OrgFeatureOverride> override =
                overrideRepository.findByOrgIdAndFeature(orgId, feature);
        if (override.isPresent()) {
            log.debug("Feature {} for org {}: override={}", feature, orgId, override.get().isEnabled());
            return override.get().isEnabled();
        }

        // 2. Fall back to tier
        Tier tier = resolveTier(orgId);
        boolean result = tier.includes(feature);
        log.debug("Feature {} for org {}: tier={} result={}", feature, orgId, tier, result);
        return result;
    }

    @Override
    public Map<Feature, Boolean> getFeatureStatus(UUID orgId) {
        Map<Feature, Boolean> status = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            status.put(f, isEnabled(orgId, f));
        }
        return status;
    }

    @Override
    public Tier getTier(UUID orgId) {
        return resolveTier(orgId);
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void setTier(UUID orgId, Tier tier) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NoSuchElementException("Organization not found: " + orgId));
        org.setTier(tier);
        organizationRepository.save(org);
        log.info("Org {} tier updated to {}", orgId, tier);
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void setOverride(UUID orgId, Feature feature, boolean enabled, String reason) {
        OrgFeatureOverride override = overrideRepository
                .findByOrgIdAndFeature(orgId, feature)
                .orElse(OrgFeatureOverride.builder()
                        .orgId(orgId)
                        .feature(feature)
                        .build());
        override.setEnabled(enabled);
        override.setReason(reason);
        overrideRepository.save(override);
        log.info("Feature override set: org={} feature={} enabled={} reason={}", orgId, feature, enabled, reason);
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE, allEntries = true)
    public void removeOverride(UUID orgId, Feature feature) {
        overrideRepository.deleteByOrgIdAndFeature(orgId, feature);
        log.info("Feature override removed: org={} feature={}", orgId, feature);
    }

    @Override
    @CacheEvict(value = CACHE, allEntries = true)
    public void evictCache(UUID orgId) {
        log.debug("Feature flag cache evicted for org {}", orgId);
    }

    @Override
    public Set<Feature> featuresForTier(Tier tier) {
        return tier.getFeatures();
    }

    private Tier resolveTier(UUID orgId) {
        return organizationRepository.findById(orgId)
                .map(Organization::getTier)
                .orElse(Tier.BASE);
    }
}
