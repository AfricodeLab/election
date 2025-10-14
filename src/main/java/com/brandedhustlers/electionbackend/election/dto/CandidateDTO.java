package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

public record CandidateDTO(
        UUID candidateId,
        String affiliation,
        String manifesto,
        String candidatePhotoURL,
        LocalDate dateOfBirth,
        String placeOfBirth,
        boolean isIncumbent,
        DeleteYn deleteYn,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
