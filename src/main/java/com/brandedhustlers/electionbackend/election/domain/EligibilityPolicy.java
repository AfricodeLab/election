package com.brandedhustlers.electionbackend.election.domain;

import lombok.*;

import java.util.List;

/**
 * Eligibility policy for an election.
 * Defines rules that voters must satisfy to cast a vote.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibilityPolicy {

    /**
     * All conditions in this list must be true (AND logic).
     */
    private List<PolicyRule> all;

    /**
     * At least one condition in this list must be true (OR logic).
     */
    private List<PolicyRule> any;

    /**
     * When true, a voter missing any required attribute is DENIED (fail-closed).
     * When false (default), a missing attribute skips the rule (fail-open).
     *
     * Use strictMode=true for national/formal elections.
     * Use strictMode=false (default) for internal corporate votes where eligibility is pre-vetted.
     */
    @Builder.Default
    private boolean strictMode = false;

    /**
     * Policy description (for display to admins/voters).
     */
    private String description;
}