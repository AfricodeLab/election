package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutBoxRepository extends JpaRepository<OutBox, UUID> {

    // Batch-limited: fetches at most `pageable.getPageSize()` pending entries
    // per scheduler tick instead of the entire unpublished backlog.
    // Caller passes PageRequest.of(0, BATCH_SIZE) — ORDER BY createdAt ensures
    // FIFO processing so no entry starves.
    @Query("select o from OutBox o where o.publishedAt is NULL and o.publishStatus is NULL ORDER BY o.createdAt ASC")
    List<OutBox> findPendingBatch(Pageable pageable);

    @Query("select o from OutBox o where o.aggregateId = :voteId")
    Optional<OutBox> findByAggregateId(UUID voteId);

    // Batch-capped anchor query: ORDER BY createdAt ensures FIFO so no entry
    // starves during a backlog. Caller passes PageRequest.of(0, ANCHOR_BATCH_SIZE).
    // Replaces the unbounded findByAnchorPendingTrueAndAnchoredFalse() which could
    // load thousands of rows into heap during a blockchain outage.
    @Query("SELECT o FROM OutBox o WHERE o.anchorPending = true AND o.anchored = false ORDER BY o.createdAt ASC")
    List<OutBox> findPendingAnchorBatch(Pageable pageable);

    // NEW: Count anchored votes for an election
    @Query("SELECT COUNT(o) FROM OutBox o WHERE o.electionId = :electionId AND o.anchored = true")
    long countByElectionIdAndAnchoredTrue(@Param("electionId") UUID electionId);

    // NEW: Count pending anchor votes
    @Query("SELECT COUNT(o) FROM OutBox o WHERE o.electionId = :electionId AND o.anchorPending = true")
    long countByElectionIdAndAnchorPendingTrue(@Param("electionId") UUID electionId);



}

