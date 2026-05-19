package com.brandedhustlers.electionbackend.results.repository;

import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TallyRepository extends JpaRepository<Tally, TallyId> {

    // Non-paginated — kept for internal aggregation (e.g. totalVotes cross-checks)
    List<Tally> findByIdElectionId(UUID electionId);

    // DB-level pagination for the results API endpoint
    Page<Tally> findByIdElectionId(UUID electionId, Pageable pageable);
}