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

    @Column(nullable = false, unique = true)
    private String candidateName;

    @Column(columnDefinition = "TEXT")
    private String manifesto;

    private String candidatePhotoURL;

    private LocalDate dateOfBirth;

    private String placeOfBirth;

    private boolean isIncumbent;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;

    @ManyToOne
    @JoinColumn(name = "election_id")
    private Election election;

    @ManyToOne
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
