package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
}
