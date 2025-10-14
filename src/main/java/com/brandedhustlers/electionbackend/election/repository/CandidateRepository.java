package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    @Query("select c from Candidate c where c.candidateId = :candidateId and c.election.electionId = :electionId")
    Candidate findByCandidateIdAndElection(@Param("candidateId") UUID candidateId,
                                           @Param("electionId") UUID electionId);
}
