package com.brandedhustlers.electionbackend.voting.dto;

import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Canonical representation of election config for anchoring.
 * Only includes fields that affect vote integrity.
 */
@Getter
@Setter
@Builder
public class ElectionConfigDTO {
    private UUID electionId;
    private UUID orgId;
    private String title;
    private EligibilityPolicy eligibilityPolicy;
    private Integer policyVersion;
    private Timestamp startTime;
    private Timestamp endTime;
    private List<CandidateInfo> candidates;

    /**
     * Simplified candidate info for config snapshot.
     */
    public record CandidateInfo(UUID candidateId, String name, Integer position) {}
}
