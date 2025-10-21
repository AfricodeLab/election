package com.brandedhustlers.electionbackend.ops.dto;

import java.util.UUID;

public record UpsertStation(
        UUID launchId,
        String launchName,
        String location,
        Integer capacity
) {
}
