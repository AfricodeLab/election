package com.brandedhustlers.electionbackend.results.repository;

import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TallyRepository extends JpaRepository<Tally, TallyId> {
    List<Tally> findByIdElectionId(UUID electionId);
}