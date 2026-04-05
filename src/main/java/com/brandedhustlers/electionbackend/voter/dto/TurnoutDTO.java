package com.brandedhustlers.electionbackend.voter.dto;

import java.util.UUID;

public record TurnoutDTO(
        UUID electionId,
        long registered,
        long voted,
        double turnoutPercent) {
}
