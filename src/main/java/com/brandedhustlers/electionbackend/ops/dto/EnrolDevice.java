package com.brandedhustlers.electionbackend.ops.dto;

import java.util.UUID;

public record EnrolDevice(
        UUID stationId,
        String label,
        String appVersion
){
}
