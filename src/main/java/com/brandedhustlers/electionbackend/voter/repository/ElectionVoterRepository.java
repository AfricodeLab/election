package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, UUID> {
    @Query("SELECT ev.id.voterId FROM ElectionVoter ev WHERE ev.id.electionId = :electionId AND ev.id.voterId IN :voterIds")
    List<UUID> findAssignedVoterIds(@Param("electionId") UUID electionId, @Param("voterIds") List<UUID> voterIds);
}
