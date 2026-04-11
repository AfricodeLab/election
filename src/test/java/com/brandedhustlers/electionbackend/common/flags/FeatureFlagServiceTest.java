package com.brandedhustlers.electionbackend.common.flags;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagService")
class FeatureFlagServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrgFeatureOverrideRepository overrideRepository;

    private FeatureFlagServiceImpl service;

    private UUID orgId;
    private Organization baseOrg;
    private Organization enterpriseOrg;

    @BeforeEach
    void setUp() {
        service = new FeatureFlagServiceImpl(organizationRepository, overrideRepository);
        orgId = UUID.randomUUID();

        baseOrg = new Organization();
        baseOrg.setOrganizationId(orgId);
        baseOrg.setTier(Tier.BASE);

        enterpriseOrg = new Organization();
        enterpriseOrg.setOrganizationId(orgId);
        enterpriseOrg.setTier(Tier.ENTERPRISE);
    }

    @Nested
    @DisplayName("isEnabled — tier resolution")
    class TierResolution {

        @Test
        @DisplayName("Returns true for BASE feature when org is on BASE tier")
        void voteCastingEnabledOnBase() {
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.VOTE_CASTING))
                    .thenReturn(Optional.empty());
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(baseOrg));

            assertTrue(service.isEnabled(orgId, Feature.VOTE_CASTING));
        }

        @Test
        @DisplayName("Returns false for INTEGRITY feature when org is on BASE tier")
        void blockchainAnchoringDisabledOnBase() {
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.BLOCKCHAIN_ANCHORING))
                    .thenReturn(Optional.empty());
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(baseOrg));

            assertFalse(service.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING));
        }

        @Test
        @DisplayName("Returns true for COMPLIANCE feature when org is on ENTERPRISE tier")
        void auditTrailEnabledOnEnterprise() {
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.AUDIT_TRAIL))
                    .thenReturn(Optional.empty());
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(enterpriseOrg));

            assertTrue(service.isEnabled(orgId, Feature.AUDIT_TRAIL));
        }

        @Test
        @DisplayName("Returns true for all ENTERPRISE features when org is on ENTERPRISE tier")
        void allEnterpriseFeaturesEnabled() {
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(enterpriseOrg));
            when(overrideRepository.findByOrgIdAndFeature(eq(orgId), any())).thenReturn(Optional.empty());

            for (Feature f : Tier.ENTERPRISE.getFeatures()) {
                assertTrue(service.isEnabled(orgId, f),
                        "Expected " + f + " to be enabled on ENTERPRISE tier");
            }
        }

        @Test
        @DisplayName("Falls back to BASE tier when org not found")
        void fallsBackToBaseWhenOrgNotFound() {
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.BLOCKCHAIN_ANCHORING))
                    .thenReturn(Optional.empty());
            when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

            // BLOCKCHAIN_ANCHORING is not in BASE tier
            assertFalse(service.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING));
        }
    }

    @Nested
    @DisplayName("isEnabled — override resolution")
    class OverrideResolution {

        @Test
        @DisplayName("Override enables feature even when below required tier")
        void overrideEnablesFeatureBelowTier() {
            OrgFeatureOverride override = OrgFeatureOverride.builder()
                    .orgId(orgId)
                    .feature(Feature.BLOCKCHAIN_ANCHORING)
                    .enabled(true)
                    .reason("Trial access")
                    .build();

            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.BLOCKCHAIN_ANCHORING))
                    .thenReturn(Optional.of(override));

            // BASE org, but override enables BLOCKCHAIN_ANCHORING
            assertTrue(service.isEnabled(orgId, Feature.BLOCKCHAIN_ANCHORING));
            // org tier is never consulted when override is present
            verify(organizationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Override disables feature even when included in tier")
        void overrideDisablesFeatureInTier() {
            OrgFeatureOverride override = OrgFeatureOverride.builder()
                    .orgId(orgId)
                    .feature(Feature.VOTE_CASTING)
                    .enabled(false)
                    .reason("Account suspended")
                    .build();

            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.VOTE_CASTING))
                    .thenReturn(Optional.of(override));

            // VOTE_CASTING is in BASE, but override disables it
            assertFalse(service.isEnabled(orgId, Feature.VOTE_CASTING));
            verify(organizationRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("getFeatureStatus")
    class FeatureStatus {

        @Test
        @DisplayName("Returns status map with all Feature keys")
        void returnsAllFeatures() {
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(baseOrg));
            when(overrideRepository.findByOrgIdAndFeature(eq(orgId), any())).thenReturn(Optional.empty());

            var status = service.getFeatureStatus(orgId);

            assertEquals(Feature.values().length, status.size());
            assertTrue(status.containsKey(Feature.VOTE_CASTING));
            assertTrue(status.containsKey(Feature.BLOCKCHAIN_ANCHORING));
        }
    }

    @Nested
    @DisplayName("setTier")
    class SetTier {

        @Test
        @DisplayName("Updates org tier and saves")
        void updatesTierAndSaves() {
            when(organizationRepository.findById(orgId)).thenReturn(Optional.of(baseOrg));

            service.setTier(orgId, Tier.INTEGRITY);

            ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(captor.capture());
            assertEquals(Tier.INTEGRITY, captor.getValue().getTier());
        }

        @Test
        @DisplayName("Throws NoSuchElementException when org not found")
        void throwsWhenOrgNotFound() {
            when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

            assertThrows(java.util.NoSuchElementException.class,
                    () -> service.setTier(orgId, Tier.ENTERPRISE));
        }
    }

    @Nested
    @DisplayName("setOverride")
    class SetOverride {

        @Test
        @DisplayName("Creates new override when none exists")
        void createsNewOverride() {
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.REAL_TIME_RESULTS))
                    .thenReturn(Optional.empty());

            service.setOverride(orgId, Feature.REAL_TIME_RESULTS, true, "Trial period");

            ArgumentCaptor<OrgFeatureOverride> captor = ArgumentCaptor.forClass(OrgFeatureOverride.class);
            verify(overrideRepository).save(captor.capture());
            assertTrue(captor.getValue().isEnabled());
            assertEquals("Trial period", captor.getValue().getReason());
        }

        @Test
        @DisplayName("Updates existing override")
        void updatesExistingOverride() {
            OrgFeatureOverride existing = OrgFeatureOverride.builder()
                    .orgId(orgId).feature(Feature.REAL_TIME_RESULTS).enabled(true).build();
            when(overrideRepository.findByOrgIdAndFeature(orgId, Feature.REAL_TIME_RESULTS))
                    .thenReturn(Optional.of(existing));

            service.setOverride(orgId, Feature.REAL_TIME_RESULTS, false, "Account expired");

            ArgumentCaptor<OrgFeatureOverride> captor = ArgumentCaptor.forClass(OrgFeatureOverride.class);
            verify(overrideRepository).save(captor.capture());
            assertFalse(captor.getValue().isEnabled());
        }
    }

    @Nested
    @DisplayName("Tier.minimumTierFor")
    class MinimumTier {

        @Test
        @DisplayName("BASE features map to BASE tier")
        void baseFeaturesMapsToBase() {
            assertEquals(Tier.BASE, Tier.minimumTierFor(Feature.VOTE_CASTING));
            assertEquals(Tier.BASE, Tier.minimumTierFor(Feature.ELECTION_MANAGEMENT));
        }

        @Test
        @DisplayName("INTEGRITY features map to INTEGRITY tier")
        void integrityFeaturesMapsToIntegrity() {
            assertEquals(Tier.INTEGRITY, Tier.minimumTierFor(Feature.BLOCKCHAIN_ANCHORING));
            assertEquals(Tier.INTEGRITY, Tier.minimumTierFor(Feature.MERKLE_VERIFICATION));
        }

        @Test
        @DisplayName("COMPLIANCE features map to COMPLIANCE tier")
        void complianceFeaturesMapsToCompliance() {
            assertEquals(Tier.COMPLIANCE, Tier.minimumTierFor(Feature.AUDIT_TRAIL));
            assertEquals(Tier.COMPLIANCE, Tier.minimumTierFor(Feature.TAMPER_ALERTS));
            assertEquals(Tier.COMPLIANCE, Tier.minimumTierFor(Feature.PII_REDACTION));
        }

        @Test
        @DisplayName("ENTERPRISE features map to ENTERPRISE tier")
        void enterpriseFeaturesMapsToEnterprise() {
            assertEquals(Tier.ENTERPRISE, Tier.minimumTierFor(Feature.REAL_TIME_RESULTS));
            assertEquals(Tier.ENTERPRISE, Tier.minimumTierFor(Feature.ADVANCED_RESILIENCE));
        }
    }
}
