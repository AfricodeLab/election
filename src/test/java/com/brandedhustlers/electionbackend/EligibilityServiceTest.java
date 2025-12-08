package com.brandedhustlers.electionbackend;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.domain.PolicyRule;
import com.brandedhustlers.electionbackend.election.service.EligibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @InjectMocks
    private EligibilityService eligibilityService;

    @Test
    void testSimplePolicy() {
        // Voter attributes
        Map<String, Object> attributes = Map.of(
                "age", 25,
                "region", "Greater Accra",
                "citizen", true
        );

        // Policy: must be 18+ and citizen
        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build(),
                        PolicyRule.builder().field("citizen").op("==").value(true).build()
                ))
                .build();

        // Evaluate
        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
    }

    @Test
    void testFailsAgeRequirement() {
        Map<String, Object> attributes = Map.of("age", 16, "citizen", true);

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("age >= 18"));
    }

    @Test
    void testAnyCondition() {
        Map<String, Object> attributes = Map.of("role", "staff");

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .any(List.of(
                        PolicyRule.builder().field("role").op("in")
                                .value(List.of("student", "staff", "faculty")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertTrue(result.isAllowed());
    }

    @Test
    void testNullPolicy() {
        Map<String, Object> attributes = Map.of("age", 25);

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, null);

        assertNotNull(result);
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
    }

    @Test
    void testNullOrEmptyAttributes() {
        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build()
                ))
                .build();

        EligibilityService.EligibilityResult resultNull =
                eligibilityService.evaluate(null, policy);

        assertNotNull(resultNull);
        assertFalse(resultNull.isAllowed());
        assertEquals("Voter has no attributes configured", resultNull.getReason());

        EligibilityService.EligibilityResult resultEmpty =
                eligibilityService.evaluate(new HashMap<>(), policy);

        assertNotNull(resultEmpty);
        assertFalse(resultEmpty.isAllowed());
        assertEquals("Voter has no attributes configured", resultEmpty.getReason());
    }

    @Test
    void testMissingAttribute() {
        Map<String, Object> attributes = Map.of("age", 25);

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("citizen").op("==").value(true).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("citizen == true"));
    }

    @Test
    void testEqualityOperators() {
        Map<String, Object> attributes = Map.of("status", "active");

        // Test ==
        EligibilityPolicy policyEquals = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("status").op("==").value("active").build()
                ))
                .build();

        EligibilityService.EligibilityResult resultEquals =
                eligibilityService.evaluate(attributes, policyEquals);

        assertTrue(resultEquals.isAllowed());

        // Test !=
        EligibilityPolicy policyNotEquals = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("status").op("!=").value("inactive").build()
                ))
                .build();

        EligibilityService.EligibilityResult resultNotEquals =
                eligibilityService.evaluate(attributes, policyNotEquals);

        assertTrue(resultNotEquals.isAllowed());
    }

    @Test
    void testComparisonOperators() {
        Map<String, Object> attributes = Map.of("age", 30);

        // Test >
        EligibilityPolicy policyGreaterThan = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">").value(18).build()
                ))
                .build();

        assertTrue(eligibilityService.evaluate(attributes, policyGreaterThan).isAllowed());

        // Test <
        EligibilityPolicy policyLessThan = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op("<").value(40).build()
                ))
                .build();

        assertTrue(eligibilityService.evaluate(attributes, policyLessThan).isAllowed());

        // Test <=
        EligibilityPolicy policyLessEquals = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op("<=").value(30).build()
                ))
                .build();

        assertTrue(eligibilityService.evaluate(attributes, policyLessEquals).isAllowed());
    }

    @Test
    void testInOperator() {
        Map<String, Object> attributes = Map.of("department", "Engineering");

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("department").op("in")
                                .value(List.of("Engineering", "Sales", "Marketing")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertTrue(result.isAllowed());
    }

    @Test
    void testNotInOperator() {
        Map<String, Object> attributes = Map.of("department", "HR");

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("department").op("not_in")
                                .value(List.of("Engineering", "Sales")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertTrue(result.isAllowed());
    }

    @Test
    void testFailsAnyCondition() {
        Map<String, Object> attributes = Map.of("role", "contractor");

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .any(List.of(
                        PolicyRule.builder().field("role").op("in")
                                .value(List.of("student", "staff", "faculty")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertFalse(result.isAllowed());
        assertEquals("Does not meet any of the alternative requirements", result.getReason());
    }

    @Test
    void testComplexPolicyAllAndAny() {
        Map<String, Object> attributes = Map.of(
                "age", 20,
                "citizen", true,
                "role", "student"
        );

        // Must be 18+ AND citizen AND (student OR staff)
        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build(),
                        PolicyRule.builder().field("citizen").op("==").value(true).build()
                ))
                .any(List.of(
                        PolicyRule.builder().field("role").op("in")
                                .value(List.of("student", "staff")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertTrue(result.isAllowed());
    }

    @Test
    void testFailsAllButPassesAny() {
        Map<String, Object> attributes = Map.of(
                "age", 16,
                "citizen", true,
                "role", "student"
        );

        // Must be 18+ AND citizen AND (student OR staff)
        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build(),
                        PolicyRule.builder().field("citizen").op("==").value(true).build()
                ))
                .any(List.of(
                        PolicyRule.builder().field("role").op("in")
                                .value(List.of("student", "staff")).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("age >= 18"));
    }

    @Test
    void testMultipleFailedAllConditions() {
        Map<String, Object> attributes = Map.of(
                "age", 15,
                "citizen", false
        );

        EligibilityPolicy policy = EligibilityPolicy.builder()
                .all(List.of(
                        PolicyRule.builder().field("age").op(">=").value(18).build(),
                        PolicyRule.builder().field("citizen").op("==").value(true).build()
                ))
                .build();

        EligibilityService.EligibilityResult result =
                eligibilityService.evaluate(attributes, policy);

        assertNotNull(result);
        assertFalse(result.isAllowed());
        // Should fail on first condition (age)
        assertTrue(result.getReason().contains("age >= 18"));
    }
}