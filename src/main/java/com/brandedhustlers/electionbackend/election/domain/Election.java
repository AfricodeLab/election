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
@Table(name = "election", schema = "election")
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

    private Timestamp electionStartTime;

    private Timestamp electionEndTime;

    private Long totalVoters;

    private Long expectedVoterCount;

    private Long totalVotes;

    @OneToMany(mappedBy = "election", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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

    @OneToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @OneToOne
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;


    private boolean isPubliclyVisible = false;

    @ManyToOne
    private Launch launch;

    private boolean shouldEndAutomatically = true; //if true election will end automatically if the total expected votes is reached
}
