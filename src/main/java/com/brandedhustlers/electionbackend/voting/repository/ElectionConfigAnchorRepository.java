package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.ElectionConfigAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

import java.util.List;

public interface ElectionConfigAnchorRepository extends JpaRepository<ElectionConfigAnchor, UUID> {
    List<ElectionConfigAnchor> findByChainTxId(String chainTxId);
}
