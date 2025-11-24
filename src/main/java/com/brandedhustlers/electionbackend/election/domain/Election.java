package com.brandedhustlers.electionbackend.election.domain;


import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.enums.ElectionType;
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
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

    @Enumerated(EnumType.STRING)
    private ElectionType electionType;

    @Enumerated(EnumType.STRING)
    private ElectionStatus electionStatus;

    @Column(updatable = false)
    private Timestamp electionStartTime;

    @Column(updatable = false)
    private Timestamp electionEndTime;

    private Long totalVoters;

    private Long expectedVoterCount;

    private Long totalVotes;

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Candidate> candidates = new ArrayList<>();

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn = DeleteYn.N;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;


    private boolean isPubliclyVisible = false;

    @ManyToOne
    private Launch launch;

    private boolean shouldEndAutomatically = true; //if true election will end automatically if the total expected votes is reached

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
}

//todo: work on voter eligibility, based on age, position, etc, vote and voter is 1 : 1,