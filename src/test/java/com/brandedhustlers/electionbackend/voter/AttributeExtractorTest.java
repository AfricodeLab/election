package com.brandedhustlers.electionbackend.voter;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.domain.PolicyRule;
import com.brandedhustlers.electionbackend.voter.service.AttributeExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for AttributeExtractor — no mocks needed (no Spring deps).
 */
class AttributeExtractorTest {

    private AttributeExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AttributeExtractor();
    }

    // ─── Age extraction ───────────────────────────────────────────────────────

    @Test
    void extractsAgeFromValidGhanaId() {
        // Format: GHA-YYYY XXXXX-D
        int birthYear = LocalDate.now().getYear() - 30;
        String id = "GHA-" + birthYear + "12345-1";

        Map<String, Object> attrs = extractor.extractAttributes(id, null, null, null);

        assertThat(attrs).containsKey("age");
        assertThat((Integer) attrs.get("age")).isBetween(29, 31); // allow ±1 for year boundary
    }

    @Test
    void ageExtractionReturnsNullForInvalidIdFormat() {
        Map<String, Object> attrs = extractor.extractAttributes("INVALID-ID", null, null, null);
        // No "age" key since extraction failed
        assertThat(attrs).doesNotContainKey("age");
    }

    // ─── Region extraction ────────────────────────────────────────────────────

    @Test
    void extractsGreaterAccraFromPrefix024() {
        Map<String, Object> attrs = extractor.extractAttributes(null, "0241234567", null, null);
        assertThat(attrs.get("region")).isEqualTo("Greater Accra");
    }

    @Test
    void extractsGreaterAccraFromPrefix054() {
        Map<String, Object> attrs = extractor.extractAttributes(null, "0541234567", null, null);
        assertThat(attrs.get("region")).isEqualTo("Greater Accra");
    }

    @Test
    void extractsAshlantiFromPrefix020() {
        Map<String, Object> attrs = extractor.extractAttributes(null, "0201234567", null, null);
        assertThat(attrs.get("region")).isEqualTo("Ashanti");
    }

    @Test
    void normalizesCountryCodePrefixBeforeExtractingRegion() {
        // +233 followed by 24... → 024... → Greater Accra
        Map<String, Object> attrs = extractor.extractAttributes(null, "+233241234567", null, null);
        assertThat(attrs.get("region")).isEqualTo("Greater Accra");
    }

    // ─── Default citizen flag ─────────────────────────────────────────────────

    @Test
    void defaultsCitizenToTrueWhenNotProvided() {
        Map<String, Object> attrs = extractor.extractAttributes(null, null, null, null);
        assertThat(attrs.get("citizen")).isEqualTo(true);
    }

    @Test
    void doesNotOverrideExplicitCitizenFalse() {
        Map<String, Object> provided = new HashMap<>();
        provided.put("citizen", false);

        Map<String, Object> attrs = extractor.extractAttributes(null, null, null, provided);
        assertThat(attrs.get("citizen")).isEqualTo(false);
    }

    // ─── Attribute merging ────────────────────────────────────────────────────

    @Test
    void mergesProvidedAttributesWithDerived() {
        Map<String, Object> provided = new HashMap<>();
        provided.put("department", "Engineering");
        provided.put("age", 99); // explicit age should NOT be overridden by derived

        int birthYear = LocalDate.now().getYear() - 30;
        String id = "GHA-" + birthYear + "12345-1";

        Map<String, Object> attrs = extractor.extractAttributes(id, "0241234567", null, provided);

        assertThat(attrs.get("department")).isEqualTo("Engineering");
        assertThat(attrs.get("age")).isEqualTo(99); // explicit wins
        assertThat(attrs.get("region")).isEqualTo("Greater Accra");
    }

    @Test
    void alwaysContainsExtractedAtTimestamp() {
        Map<String, Object> attrs = extractor.extractAttributes(null, null, null, null);
        assertThat(attrs).containsKey("extracted_at");
        assertThat(attrs.get("extracted_at")).isInstanceOf(Long.class);
    }

    // ─── validateRequiredAttributes ───────────────────────────────────────────

    @Test
    void returnsEmptyListWhenAllRequiredAttributesPresent() {
        PolicyRule rule = new PolicyRule();
        rule.setField("age");

        EligibilityPolicy policy = new EligibilityPolicy();
        policy.setAll(List.of(rule));

        Map<String, Object> attrs = Map.of("age", 25);
        List<String> missing = extractor.validateRequiredAttributes(attrs, policy);

        assertThat(missing).isEmpty();
    }

    @Test
    void returnsMissingFieldsWhenAttributeAbsent() {
        PolicyRule rule = new PolicyRule();
        rule.setField("region");

        EligibilityPolicy policy = new EligibilityPolicy();
        policy.setAll(List.of(rule));

        List<String> missing = extractor.validateRequiredAttributes(Map.of(), policy);

        assertThat(missing).containsExactly("region");
    }

    @Test
    void returnsEmptyListWhenPolicyIsNull() {
        List<String> missing = extractor.validateRequiredAttributes(Map.of("age", 25), null);
        assertThat(missing).isEmpty();
    }
}
