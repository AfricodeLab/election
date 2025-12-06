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
            return EligibilityResult.deny("Voter has no attributes configured");
        }

        try {
            // Evaluate "all" conditions (AND logic)
            if (policy.getAll() != null && !policy.getAll().isEmpty()) {
                for (PolicyRule rule : policy.getAll()) {
                    if (!evaluateRule(voterAttributes, rule)) {
                        return EligibilityResult.deny(
                                String.format("Failed requirement: %s %s %s",
                                        rule.getField(), rule.getOp(), rule.getValue())
                        );
                    }
                }
            }

            // Evaluate "any" conditions (OR logic)
            if (policy.getAny() != null && !policy.getAny().isEmpty()) {
                boolean anyMatched = false;
                for (PolicyRule rule : policy.getAny()) {
                    if (evaluateRule(voterAttributes, rule)) {
                        anyMatched = true;
                        break;
                    }
                }
                if (!anyMatched) {
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
     */
    private boolean evaluateRule(Map<String, Object> attributes, PolicyRule rule) {
        Object actualValue = attributes.get(rule.getField());

        if (actualValue == null) {
            log.warn("Voter missing attribute: {}", rule.getField());
            return false;
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