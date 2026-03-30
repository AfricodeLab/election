package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.EligibleVoterElection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EligibleVoterElectionRepository extends JpaRepository<EligibleVoterElection, UUID> {

    @Query("SELECT e.voterId FROM EligibleVoterElection e WHERE e.launchId = :launchId")
    List<UUID> findVoterIdsByLaunchId(@Param("launchId") UUID launchId);

    @Query("SELECT DISTINCT e.voterId FROM EligibleVoterElection e WHERE e.launchId = :launchId")
    List<UUID> findDistinctVoterIdsByLaunchId(@Param("launchId") UUID launchId);

    @Query("SELECT e FROM EligibleVoterElection e WHERE e.launchId = :launchId AND e.voterId = :voterId")
    List<EligibleVoterElection> findByLaunchIdAndVoterId(@Param("launchId") UUID launchId,
                                                          @Param("voterId") UUID voterId);

    List<EligibleVoterElection> findByLaunchId(UUID launchId);

    void deleteByLaunchId(UUID launchId);
}
