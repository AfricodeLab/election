package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.TamperingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TamperingLogRepository extends JpaRepository<TamperingLog, UUID> {
    /**
     * Get all tampering incidents for an election.
     * Used during release validation.
     */
    List<TamperingLog> findByElectionId(UUID electionId);

    /**
     * Get specific type of incidents (e.g., only ARCHIVE_TAMPERED).
     * Used to find votes that couldn't be restored.
     */
    List<TamperingLog> findByElectionIdAndIncidentType(UUID electionId, String incidentType);

    /**
     * Check if a specific vote has tampering history.
     */
    List<TamperingLog> findByVoteId(UUID voteId);
}
