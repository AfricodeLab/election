package com.brandedhustlers.electionbackend.election.domain;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "candidate", schema = "election")
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID candidateId;

    @Column(nullable = false)
    @Size(max = 25)
    private String affiliation;

    @Column(nullable = false)
    private String candidateName;

    @Column(columnDefinition = "TEXT")
    private String manifesto;

    private Integer candidatePosition;

    private String candidatePhotoURL;

    private LocalDate dateOfBirth;

    private String placeOfBirth;

    private boolean isIncumbent;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;

    // LAZY: this back-reference is never read during DTO mapping.
    // Keeping it EAGER caused every Candidate load to reload the parent Election
    // and then load Election.createdBy / Election.updatedBy AppUser chains.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    // LAZY: candidateToDto() doesn't use createdBy, so we don't need the full
    // AppUser graph (roles × permissions + userOrganizations) loaded per candidate.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate(){
        this.deleteYn = DeleteYn.N;
    }

}
