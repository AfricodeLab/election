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

    /**
     * Find the most recent valid (non-used, non-deleted, not-yet-expired) token
     * for a specific voter + election combination.
     *
     * <p>Covers both OTP tokens (remote) and STATION_PIN tokens (in-person).</p>
     */
    @Query("""
            SELECT vr FROM VerificationToken vr
            WHERE vr.voterId = :voterId
              AND vr.electionId = :electionId
              AND vr.isUsed = false
              AND vr.deleteYn = 'N'
              AND vr.expiresAt > :now
            ORDER BY vr.createdAt DESC
            LIMIT 1
            """)
    Optional<VerificationToken> findValidToken(UUID voterId, UUID electionId, Instant now);
}
