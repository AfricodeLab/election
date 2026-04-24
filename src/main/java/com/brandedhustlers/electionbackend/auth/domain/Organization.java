package com.brandedhustlers.electionbackend.auth.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.common.flags.Tier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "auth", name = "organization")
@ToString
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID organizationId;

    @Column(unique = true)
    private String organizationName;

    @Column(columnDefinition = "TEXT")
    private String organizationDescription;

    private boolean isEnabled = false;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    // LAZY: ElectionMapper no longer calls getCreatedBy() (that was a bug —
    // it was mapping mfaEnabled to isEnabled). Nothing in the hot path needs this.
    @OneToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Tier tier = Tier.BASE;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn = DeleteYn.N;
}
