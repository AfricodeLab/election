package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.domain.PolicyRule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EligibilityService {

    /**
     * Evaluate if a voter is eligible based on policy.
     *
     * @param voterAttributes Voter's attribute map (from voter.attributes)
     * @param policy Election's eligibility policy
     * @return Result with allow/deny and reason
     */
    public EligibilityResult evaluate(Map<String, Object> voterAttributes, EligibilityPolicy policy) {

        if (policy == null) {
            // No policy = everyone eligible
            return EligibilityResult.allow();
        }

        if (voterAttributes == null || voterAttributes.isEmpty()) {
            if (policy.isStrictMode()) {
                log.warn("Voter has no attributes and policy is strict — denying");
                return EligibilityResult.deny("Voter has no attributes on file. Contact your administrator.");
            }
            log.debug("Voter has no attributes — cannot evaluate policy, allowing by default");
            return EligibilityResult.allow();
        }

        try {
            // Evaluate "all" conditions (AND logic)
            // In strict mode, a missing attribute is a denial.
            // In lenient mode (default), a missing attribute skips the rule.
            if (policy.getAll() != null && !policy.getAll().isEmpty()) {
                for (PolicyRule rule : policy.getAll()) {
                    Boolean result = evaluateRule(voterAttributes, rule);
                    if (result == null) {
                        if (policy.isStrictMode()) {
                            return EligibilityResult.deny(
                                    String.format("Missing required attribute: %s", rule.getField()));
                        }
                        log.debug("Skipping 'all' rule for missing attribute: {}", rule.getField());
                        continue;
                    }
                    if (!result) {
                        return EligibilityResult.deny(
                                String.format("Failed requirement: %s %s %s",
                                        rule.getField(), rule.getOp(), rule.getValue())
                        );
                    }
                }
            }

            // Evaluate "any" conditions (OR logic)
            // In strict mode, missing attributes count as "not matching" (evaluated = true, matched = false).
            // In lenient mode, missing attributes are skipped entirely.
            if (policy.getAny() != null && !policy.getAny().isEmpty()) {
                boolean anyMatched = false;
                boolean anyEvaluated = false;
                for (PolicyRule rule : policy.getAny()) {
                    Boolean result = evaluateRule(voterAttributes, rule);
                    if (result == null) {
                        if (policy.isStrictMode()) {
                            anyEvaluated = true; // missing = not matched in strict mode
                        } else {
                            log.debug("Skipping 'any' rule for missing attribute: {}", rule.getField());
                        }
                        continue;
                    }
                    anyEvaluated = true;
                    if (result) {
                        anyMatched = true;
                        break;
                    }
                }
                if (anyEvaluated && !anyMatched) {
                    return EligibilityResult.deny(
                            "Does not meet any of the alternative requirements"
                    );
                }
            }

            return EligibilityResult.allow();

        } catch (Exception e) {
            log.error("Policy evaluation error", e);
            return EligibilityResult.deny("Policy evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Evaluate a single rule.
     * Returns true if the rule passes, false if it explicitly fails,
     * or null if the voter is missing the attribute (inconclusive).
     */
    private Boolean evaluateRule(Map<String, Object> attributes, PolicyRule rule) {
        Object actualValue = attributes.get(rule.getField());

        if (actualValue == null) {
            log.debug("Voter missing attribute '{}', skipping rule", rule.getField());
            return null;
        }

        Object expectedValue = rule.getValue();
        String operator = rule.getOp();

        switch (operator) {
            case "==":
                return actualValue.equals(expectedValue);

            case "!=":
                return !actualValue.equals(expectedValue);

            case ">":
                return compareNumbers(actualValue, expectedValue) > 0;

            case ">=":
                return compareNumbers(actualValue, expectedValue) >= 0;

            case "<":
                return compareNumbers(actualValue, expectedValue) < 0;

            case "<=":
                return compareNumbers(actualValue, expectedValue) <= 0;

            case "in":
                if (expectedValue instanceof List) {
                    return ((List<?>) expectedValue).contains(actualValue);
                }
                return false;

            case "not_in":
                if (expectedValue instanceof List) {
                    return !((List<?>) expectedValue).contains(actualValue);
                }
                return true;

            default:
                log.warn("Unknown operator: {}", operator);
                return false;
        }
    }

    /**
     * Compare two values as numbers.
     */
    private int compareNumbers(Object actual, Object expected) {
        double actualNum = toDouble(actual);
        double expectedNum = toDouble(expected);
        return Double.compare(actualNum, expectedNum);
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to number: " + value);
        }
    }

    /**
     * Result of eligibility check.
     */
    @Getter
    public static class EligibilityResult {
        private final boolean allowed;
        private final String reason;

        private EligibilityResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static EligibilityResult allow() {
            return new EligibilityResult(true, null);
        }

        public static EligibilityResult deny(String reason) {
            return new EligibilityResult(false, reason);
        }

    }
}