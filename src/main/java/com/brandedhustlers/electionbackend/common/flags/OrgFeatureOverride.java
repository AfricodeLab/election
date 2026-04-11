package com.brandedhustlers.electionbackend.common.flags;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Per-organization feature override.
 * Allows enabling or disabling a specific feature regardless of the org's Tier.
 * Use cases: trial access, custom deals, emergency kill-switch.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "auth", name = "org_feature_override",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "feature"}))
public class OrgFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Feature feature;

    /** true = force-enable (even if below required tier). false = force-disable. */
    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    private Timestamp createdAt;
}
