package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;

import java.sql.Timestamp;

public record UpdateElection(
        String newElectionName,
        ElectionStatus newElectionStatus,
        Timestamp newStartTime,
        Timestamp newEndtime
) {
}
