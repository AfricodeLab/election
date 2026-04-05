package com.brandedhustlers.electionbackend.common.flags;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.common.config.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagAspect")
class FeatureFlagAspectTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private FeatureFlagAspect aspect;
    private UUID orgId;
    private Organization org;

    @BeforeEach
    void setUp() {
        aspect = new FeatureFlagAspect(featureFlagService);
        orgId = UUID.randomUUID();
        org = new Organization();
        org.setOrganizationId(orgId);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RequiresFeature annotationFor(Feature feature) {
        RequiresFeature annotation = mock(RequiresFeature.class);
        when(annotation.value()).thenReturn(feature);
        return annotation;
    }

    private void setUpSecurityContext(UserPrincipal principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    @DisplayName("Allows through when no security context (scheduler / system context)")
    void allowsWhenNoSecurityContext() {
        // No security context set
        assertDoesNotThrow(() -> aspect.checkFeature(annotationFor(Feature.BLOCKCHAIN_ANCHORING)));
        verifyNoInteractions(featureFlagService);
    }

    @Test
    @DisplayName("Allows through when user has no active organization")
    void allowsWhenNoActiveOrg() {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getActiveOrganization()).thenReturn(null);
        setUpSecurityContext(principal);

        assertDoesNotThrow(() -> aspect.checkFeature(annotationFor(Feature.BLOCKCHAIN_ANCHORING)));
        verifyNoInteractions(featureFlagService);
    }

    @Test
    @DisplayName("Allows through when feature is enabled for the org")
    void allowsWhenFeatureEnabled() {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getActiveOrganization()).thenReturn(org);
        setUpSecurityContext(principal);

        when(featureFlagService.isEnabled(orgId, Feature.REAL_TIME_RESULTS)).thenReturn(true);

        assertDoesNotThrow(() -> aspect.checkFeature(annotationFor(Feature.REAL_TIME_RESULTS)));
        verify(featureFlagService).isEnabled(orgId, Feature.REAL_TIME_RESULTS);
    }

    @Test
    @DisplayName("Throws FeatureNotAvailableException when feature is disabled for the org")
    void throwsWhenFeatureDisabled() {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getActiveOrganization()).thenReturn(org);
        setUpSecurityContext(principal);

        when(featureFlagService.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING)).thenReturn(false);
        when(featureFlagService.getTier(orgId)).thenReturn(Tier.BASE);

        FeatureNotAvailableException ex = assertThrows(FeatureNotAvailableException.class,
                () -> aspect.checkFeature(annotationFor(Feature.BLOCKCHAIN_ANCHORING)));

        assertEquals(Feature.BLOCKCHAIN_ANCHORING, ex.getFeature());
        assertEquals(Tier.INTEGRITY, ex.getRequiredTier());
        assertTrue(ex.getMessage().contains("BLOCKCHAIN_ANCHORING"));
        assertTrue(ex.getMessage().contains("INTEGRITY"));
    }

    @Test
    @DisplayName("Exception message contains required tier name")
    void exceptionContainsRequiredTierName() {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getActiveOrganization()).thenReturn(org);
        setUpSecurityContext(principal);

        when(featureFlagService.isEnabled(orgId, Feature.REAL_TIME_RESULTS)).thenReturn(false);
        when(featureFlagService.getTier(orgId)).thenReturn(Tier.COMPLIANCE);

        FeatureNotAvailableException ex = assertThrows(FeatureNotAvailableException.class,
                () -> aspect.checkFeature(annotationFor(Feature.REAL_TIME_RESULTS)));

        assertEquals(Tier.ENTERPRISE, ex.getRequiredTier());
        assertTrue(ex.getMessage().contains("ENTERPRISE"));
    }
}
