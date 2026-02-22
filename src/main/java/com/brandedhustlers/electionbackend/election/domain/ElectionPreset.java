package com.brandedhustlers.electionbackend.election.domain;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a reusable election configuration template.
 * Admins can create a preset linked to an {@link ElectionTypeEntity},
 * optionally including a default {@link EligibilityPolicy} and voter count.
 * <p>
 * When creating a new election, an admin can pick a preset to auto-fill fields.
 */
@Entity
@Table(name = "election_preset", schema = "election")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "preset_id")
    private UUID presetId;

    /**
     * Human-readable name for this preset, e.g., "Standard Gubernatorial Setup".
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Optional description of what this preset configures.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * The election type this preset is associated with.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id", nullable = false)
    private ElectionTypeEntity electionType;

    /**
     * Organization that owns this preset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /**
     * Default eligibility policy to apply when creating an election from this
     * preset.
     * Can be overridden during election creation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_eligibility_policy", columnDefinition = "jsonb")
    private EligibilityPolicy defaultEligibilityPolicy;

    /**
     * Suggested expected voter count for this type of election.
     */
    @Column(name = "default_expected_voter_count")
    private Long defaultExpectedVoterCount;

    /**
     * Whether elections from this preset should end automatically
     * when expected votes are reached.
     */
    @Column(name = "default_should_end_automatically")
    @Builder.Default
    private boolean defaultShouldEndAutomatically = true;

    /**
     * Admin who created this preset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeleteYn deleteYn = DeleteYn.N;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
