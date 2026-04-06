package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.ElectionVoter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionVoterRepository extends JpaRepository<ElectionVoter, UUID> {
//    @Query("SELECT ev.id.voterId FROM ElectionVoter ev WHERE ev.id.electionId = :electionId AND ev.id.voterId IN :voterIds")
//    List<UUID> findAssignedVoterIds(@Param("electionId") UUID electionId, @Param("voterIds") List<UUID> voterIds);
//
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("select ev from ElectionVoter ev where ev.id.electionId = :e and ev.id.voterId = :v")
//    Optional<ElectionVoter> lockByIds(@Param("e") UUID electionId, @Param("v") UUID voterId);

    @Query("""
       SELECT ev.id.voterId
       FROM ElectionVoter ev
       WHERE ev.id.electionId = :electionId AND ev.id.voterId IN :voterIds
    """)
    List<UUID> findAssignedVoterIds(@Param("electionId") UUID electionId,
                                    @Param("voterIds") List<UUID> voterIds);


    // New: lock one row for voting (prevents double-vote under concurrency)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT ev
       FROM ElectionVoter ev
       WHERE ev.id.electionId = :electionId AND ev.id.voterId = :voterId
    """)
    Optional<ElectionVoter> lockByIds(@Param("electionId") UUID electionId,
                                      @Param("voterId") UUID voterId);

    @Query("select ev from ElectionVoter ev where ev.voter.voterId = :voterId and ev.election.electionId = :electionId")
    Optional<ElectionVoter> findByVoterIdAndElectionId(UUID voterId, UUID electionId);

    @Query("SELECT COUNT(ev) FROM ElectionVoter ev WHERE ev.id.electionId = :electionId")
    long countByElectionId(@Param("electionId") UUID electionId);

    @Query("SELECT COUNT(ev) FROM ElectionVoter ev WHERE ev.id.electionId = :electionId AND ev.hasVoted = true")
    long countVotedByElectionId(@Param("electionId") UUID electionId);


    // Optional: quick assignment check without loading the entity
/*    default boolean isAssigned(UUID electionId, UUID voterId) {
        return existsById(new ElectionVoterId(electionId, voterId));
    }*/
}
