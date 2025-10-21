package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, UUID> {
}
