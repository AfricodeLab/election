package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    @Query("select vr from VerificationToken vr where  vr.voterId = :voterId")
    Optional<VerificationToken> findTopByVoterIdAndElectionIdOrderByCreatedAtDesc(UUID voterId, UUID electionId);

    @Query("select vr from VerificationToken vr where vr.voterId = :voterId and vr.isUsed = false and vr.deleteYn = 'N' and vr.expiresAt > :now order by vr.createdAt desc limit 1")
    Optional<VerificationToken> findValidToken(UUID voterId, Instant now);

}
