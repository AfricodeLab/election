package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}

