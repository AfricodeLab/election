package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import jakarta.validation.constraints.Size;

import java.sql.Timestamp;

public record UpdateElection(
                @Size(max = 100) String newElectionName,
                ElectionStatus newElectionStatus,
                Timestamp newStartTime,
                Timestamp newEndtime) {
}
