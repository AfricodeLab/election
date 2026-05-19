package com.brandedhustlers.electionbackend.voter.domain;


import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.voter.enums.VoterStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "voters",
        schema = "voter"
)
@Builder
public class Voter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID voterId;

    // LAZY: organization is accessed via org_id scalar in most query paths.
    // EAGER (the JPA default for @ManyToOne) was pulling the full Organization
    // row on every voter load — OTP lookup, vote-cast check, voter import, etc.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    private VoterStatus voterStatus;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    // LAZY: createdBy is never projected by VoterMapper.entityToDto().
    // EAGER was loading the full AppUser graph (roles, permissions,
    // userOrganizations) on every voter fetch for no benefit.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;

    /**
     * NEW: Voter attributes for eligibility checking.
     * Stored as JSONB in PostgreSQL.
     *
     * Example:
     * {
     *   "age": 25,
     *   "region": "Greater Accra",
     *   "citizen": true,
     *   "role": "staff",
     *   "employmentStatus": "active"
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes")
    private Map<String, Object> attributes;

    /**
     * How/when attributes were sourced.
     * Example: "CSV_UPLOAD_2025-01-15", "API_SYNC", "MANUAL_ENTRY"
     */
    @Column(name = "attributes_source")
    private String attributesSource;

    @Column(name = "attributes_updated_at")
    private Timestamp attributesUpdatedAt;


    @PrePersist
    protected void onCreate(){
        this.deleteYn = DeleteYn.N;
    }
}
