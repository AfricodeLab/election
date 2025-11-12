package com.brandedhustlers.electionbackend.chain.repository;

import com.brandedhustlers.electionbackend.chain.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChainBatchRepository extends JpaRepository<Batch, UUID> {

}
