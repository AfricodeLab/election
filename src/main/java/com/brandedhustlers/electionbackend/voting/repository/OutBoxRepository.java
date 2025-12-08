package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutBoxRepository extends JpaRepository<OutBox, UUID> {
    @Query("select o from OutBox o where o.publishedAt is NULL and o.publishStatus is NULL")
    List<OutBox> findTopByPublishedAtIsNull();

    @Query("select o from OutBox o where o.aggregateId = :voteId")
    Optional<OutBox> findByAggregateId(UUID voteId);

    // NEW: Find votes ready to anchor
    List<OutBox> findByAnchorPendingTrueAndAnchoredFalse();

    // NEW: Count anchored votes for an election
    @Query("SELECT COUNT(o) FROM OutBox o WHERE o.electionId = :electionId AND o.anchored = true")
    long countByElectionIdAndAnchoredTrue(@Param("electionId") UUID electionId);

    // NEW: Count pending anchor votes
    @Query("SELECT COUNT(o) FROM OutBox o WHERE o.electionId = :electionId AND o.anchorPending = true")
    long countByElectionIdAndAnchorPendingTrue(@Param("electionId") UUID electionId);

}

