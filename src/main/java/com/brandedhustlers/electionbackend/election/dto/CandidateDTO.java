package com.brandedhustlers.electionbackend.election.dto;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

public record CandidateDTO(
                UUID candidateId,
                @NotBlank(message = "Candidate name is required") @Size(max = 100) String candidateName,
                String affiliation,
                String manifesto,
                String candidatePhotoURL,
                LocalDate dateOfBirth,
                String placeOfBirth,
                boolean isIncumbent,
                DeleteYn deleteYn,
                Timestamp createdAt,
                Timestamp updatedAt) {
}
