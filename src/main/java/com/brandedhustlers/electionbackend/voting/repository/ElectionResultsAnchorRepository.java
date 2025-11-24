package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.ElectionResultsAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ElectionResultsAnchorRepository extends JpaRepository<ElectionResultsAnchor, UUID> {
}
