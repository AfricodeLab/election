package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.ElectionConfigSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ElectionConfigSnapshotRepository extends JpaRepository<ElectionConfigSnapshot, UUID> {
    /**
     * Get all snapshots for an election, ordered chronologically.
     * Used to show config change timeline in UI.
     */
    List<ElectionConfigSnapshot> findByElectionIdOrderByCreatedAtAsc(UUID electionId);

    /**
     * Get only the change snapshots (not GO_LIVE).
     * Used during release validation to check if config drifted.
     */
    List<ElectionConfigSnapshot> findByElectionIdAndSnapshotTypeOrderByCreatedAtAsc(UUID electionId, String snapshotType);
}
