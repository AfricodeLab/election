package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {
    Optional<Vote> findByDedupeKey(String dedupeKey);


    @Query("""
     select v.id from Vote v
     where v.electionId = :electionId and v.castAt > :fromTs
     order by v.castAt asc
  """)
    List<UUID> findIdsSince(UUID electionId, Instant fromTs);

    @Query("select v from Vote v where v.electionId = :electionId and v.castAt BETWEEN :fromTs and :toTs")
    List<Vote> findIdsBetween(UUID electionId, Instant fromTs, Instant toTs);
}