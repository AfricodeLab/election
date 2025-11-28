package com.brandedhustlers.electionbackend.chain.repository;

import com.brandedhustlers.electionbackend.chain.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChainBatchRepository extends JpaRepository<Batch, UUID> {

    @Query("select b from Batch b where b.electionId = :electionId")
    List<Batch> findByElectionId(UUID electionId);

    long countByElectionId(UUID electionId);
}
