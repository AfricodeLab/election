package com.brandedhustlers.electionbackend.election.domain;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
// ElectionType enum retained for legacy/migration reference only
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "election", schema = "election", indexes = {
        @Index(name = "idx_time", columnList = "election_start_time, election_end_time")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Election {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID electionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String electionTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String electionDescription;

    /**
     * Dynamic election type (replaces old ElectionType enum).
     * Admins can create custom types; system built-in types are also stored here.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "election_type_id")
    private ElectionTypeEntity electionType;

    /**
     * Optional: the preset used to create this election.
     * Stored for audit/tracking purposes; fields may have been overridden.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preset_id")
    private ElectionPreset presetUsed;

    @Enumerated(EnumType.STRING)
    private ElectionStatus electionStatus;

    @Column(updatable = false)
    private Timestamp electionStartTime;

    @Column(updatable = false)
    private Timestamp electionEndTime;

    private Long totalVoters;

    private Long expectedVoterCount;

    private Long totalVotes;

    // LAZY: candidates are only needed for the election detail view and the vote-cast
    // candidate-validation step. List queries (findByOrganizationId) use a LEFT JOIN
    // FETCH in the repository to load them in one SQL join rather than N+1 queries.
    // The vote-cast path (VotingService) should NOT access candidates at all.
    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Candidate> candidates = new ArrayList<>();

    private Timestamp createdAt;

    private Timestamp updatedAt;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn = DeleteYn.N;

    // LAZY: neither field is projected by ElectionMapper.entityToDto().
    // Leaving them EAGER caused two full AppUser graphs (roles + permissions
    // + userOrganizations) to be loaded on every election fetch.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    private boolean isPubliclyVisible = false;

    // LAZY: not used in DTO mapping, no need to load the full Launch graph.
    @ManyToOne(fetch = FetchType.LAZY)
    private Launch launch;

    private boolean shouldEndAutomatically = true; // if true election will end automatically if the total expected
                                                   // votes is reached, or end time is reached

    /**
     * Set to true when config changes during voting.
     * Prevents auto-release until admin reviews.
     */
    @Column(name = "review_required")
    @Builder.Default
    private Boolean reviewRequired = false;

    /**
     * Human-readable reason why review is needed.
     * Example: "Candidate name changed during voting"
     */
    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    /**
     * NEW: Eligibility policy (JSON format).
     * Defines who can vote in this election.
     *
     * Example:
     * {
     * "all": [
     * {"field": "age", "op": ">=", "value": 18},
     * {"field": "citizen", "op": "==", "value": true}
     * ]
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_policy", columnDefinition = "jsonb")
    private EligibilityPolicy eligibilityPolicy;

    /**
     * Version of the policy (increment on changes).
     * Used to detect mid-election policy edits.
     */
    @Column(name = "policy_version")
    @Builder.Default
    private Integer policyVersion = 1;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = Timestamp.from(Instant.now());
        this.createdAt = Timestamp.from(Instant.now());
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Timestamp.from(Instant.now());
    }
}