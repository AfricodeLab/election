package com.brandedhustlers.electionbackend.election.domain;


import lombok.*;

/**
 * A single policy rule.
 * Example: {"field": "age", "op": ">=", "value": 18}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRule {

    /**
     * Field name in voter attributes.
     * Example: "age", "region", "citizen", "role"
     */
    private String field;

    /**
     * Comparison operator.
     * Supported: "==", "!=", ">", ">=", "<", "<=", "in", "not_in"
     */
    private String op;

    /**
     * Value to compare against.
     * Can be: Number, String, Boolean, or List (for "in"/"not_in").
     */
    private Object value;
}
