package com.brandedhustlers.electionbackend.voter.dto;

import java.util.List;
import java.util.UUID;

public record AssignVotersCmd(
        UUID electionId,
        List<UUID> voterIds
) {
}
