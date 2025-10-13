package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.election.enums.ElectionType;

import java.sql.Timestamp;

public record CreateElectionRequest(
        String electionTitle,
        String electionDescription,
        Timestamp startTime,
        Timestamp endtime,
        ElectionType electionType,
        boolean isPubliclyVisible
) {
}
