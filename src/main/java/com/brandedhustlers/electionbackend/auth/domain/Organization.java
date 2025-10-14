package com.brandedhustlers.electionbackend.auth.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
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
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID organizationId;

    @Column(nullable = false, unique = true)
    private String organizationName;

    @Column(columnDefinition = "TEXT")
    private String organizationDescription;

    private boolean isEnabled = false;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @OneToOne
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn = DeleteYn.N;
}
