package com.brandedhustlers.electionbackend.common.flags;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP aspect that enforces {@link RequiresFeature} annotations.
 *
 * Resolution order:
 *  1. No authenticated user in context (scheduled tasks, system calls) → skip check, fail open.
 *  2. Authenticated user has no active organization → skip check, fail open with warning.
 *  3. Normal web request → check FeatureFlagService for the org. Throw if disabled.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class FeatureFlagAspect {

    private final FeatureFlagService featureFlagService;

    @Before("@annotation(requiresFeature)")
    public void checkFeature(RequiresFeature requiresFeature) {
        Feature feature = requiresFeature.value();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // No security context — allow through (scheduler / system context)
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            log.debug("@RequiresFeature({}) — no authenticated user, skipping check (system context)", feature);
            return;
        }

        Organization org = principal.getActiveOrganization();
        if (org == null) {
            log.warn("@RequiresFeature({}) — authenticated user has no active organization, skipping check", feature);
            return;
        }

        UUID orgId = org.getOrganizationId();

        if (!featureFlagService.isEnabled(orgId, feature)) {
            log.warn("Feature {} blocked for org {} (tier={})", feature, orgId, featureFlagService.getTier(orgId));
            throw new FeatureNotAvailableException(feature);
        }
    }
}
