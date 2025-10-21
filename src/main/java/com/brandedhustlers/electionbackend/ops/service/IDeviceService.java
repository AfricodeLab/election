package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IDeviceService {
    Device enroll(UUID orgId, UUID stationId, String label, String appVersion);

    Optional<Device> get(UUID orgId, UUID deviceId);

    List<Device> list(UUID orgId, UUID stationId, DeviceStatus status);

    Device rebind(UUID orgId, UUID deviceId, UUID newStationId);

    Device recordHeartbeat(UUID orgId, UUID deviceId, Long latencyMs, String appVersion, Instant deviceTime);
}
