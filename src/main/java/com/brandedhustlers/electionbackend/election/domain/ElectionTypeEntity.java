package com.brandedhustlers.electionbackend.election.domain;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a dynamic election type defined by an organization (or
 * system-level presets).
 * Replaces the static
 * {@link com.brandedhustlers.electionbackend.election.enums.ElectionType} enum
 * so admins can define their own election types (e.g., "Board Vote",
 * "Departmental Poll").
 */
@Entity
@Table(name = "election_type", schema = "election", uniqueConstraints = @UniqueConstraint(name = "uq_election_type_name_org", columnNames = {
        "name", "organization_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "type_id")
    private UUID typeId;

    /**
     * Human-readable name of the election type, e.g., "Presidential", "Board
     * Election".
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Optional description to clarify the election type to admins.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * If true, this is a built-in system type seeded from the old enum.
     * Cannot be deleted.
     */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    /**
     * Organization that owns this type. Null for system-level types.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Admin who created this type.
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
