package com.brandedhustlers.electionbackend.ops.service;

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.DeviceDto;
import com.brandedhustlers.electionbackend.ops.dto.EnrolDevice;
import com.brandedhustlers.electionbackend.ops.dto.OpsPolicy;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import com.brandedhustlers.electionbackend.ops.exception.DeviceNotFound;
import com.brandedhustlers.electionbackend.ops.exception.OrgMismatch;
import com.brandedhustlers.electionbackend.ops.exception.RequiredNull;
import com.brandedhustlers.electionbackend.ops.exception.StationNotFound;
import com.brandedhustlers.electionbackend.ops.mappers.DeviceMapper;
import com.brandedhustlers.electionbackend.ops.repository.DeviceRepository;
import com.brandedhustlers.electionbackend.ops.repository.StationRepository;
import com.brandedhustlers.electionbackend.realtime.gateway.RealtimeGateway;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DeviceServiceImpl: enrollment, rebinding, and heartbeat ingestion.
 * Justification: isolates the critical health path, applies SLA thresholds, and emits realtime/audit side-effects
 * only on state change to reduce noise and avoid flapping.
 */
@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final StationRepository stations;
    private final RealtimeGateway realtimeGateway;
    private final DeviceMapper deviceMapper;

    DeviceService(DeviceRepository devices,
                  StationRepository stations,
                  RealtimeGateway realtimeGateway,
                  DeviceMapper deviceMapper) {
        this.devices = devices;
        this.stations = stations;
        this.realtimeGateway = realtimeGateway;
        this.deviceMapper = deviceMapper;
    }

    static OpsPolicy policy = new OpsPolicy(
            12233,
            12223,
            5
    );

    @Transactional
    public DeviceDto enrollDevice(UUID orgId, EnrolDevice enrolDevice) {
        Station st = stations.findById(enrolDevice.stationId())
                .orElseThrow(() -> new StationNotFound("Station not found"));
        if (!st.getOrgId().equals(orgId)) {
            throw new OrgMismatch("Org mismatch");
        }
        if (enrolDevice.label() == null || enrolDevice.label().isBlank()) {
            throw new RequiredNull("Device label required");
        }
        Device device = Device.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .stationId(enrolDevice.stationId())
                .label(enrolDevice.label())
                .status(DeviceStatus.UNKNOWN)
                .lastSeen(null)
                .lastLatencyMs(null)
                .appVersion(enrolDevice.appVersion())
                .createdAt(Instant.now())
                .build();


        // TODO: audit.emit("ops.device.enrolled", saved)
        return deviceMapper
                .entityToDto(devices.save(device));
    }

    public Optional<DeviceDto> get(UUID orgId, UUID deviceId) {
        return devices.findById(deviceId)
                .filter(d -> d.getOrgId().equals(orgId))
                .map(deviceMapper::entityToDto);
    }

    public Page<DeviceDto> listDevices(UUID orgId, UUID stationId, DeviceStatus status, Pageable pageable) {
        // Minimal filters; can add dedicated repo queries as needed
        List<DeviceDto> deviceDtos = devices.findAll()
                .stream()
                .filter(d -> d.getOrgId().equals(orgId))
                .filter(d -> stationId == null || stationId.equals(d.getStationId()))
                .filter(d -> status == null || status == d.getStatus())
                .map(deviceMapper::entityToDto)
                .toList();

        return new PageImpl<>(deviceDtos, pageable, deviceDtos.size());
    }

    @Transactional
    public DeviceDto rebind(UUID orgId, UUID deviceId, UUID newStationId) {
        Device d = devices.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFound("Device not found"));

        if (!d.getOrgId().equals(orgId)) {
            throw new OrgMismatch("Org mismatch");
        }
        Station st = stations.findById(newStationId)
                .orElseThrow(() -> new StationNotFound("Station not found"));

        if (!st.getOrgId().equals(orgId)) {
            throw new SecurityException("Org mismatch");
        }
        d.setStationId(newStationId);
        return deviceMapper
                .entityToDto(devices.save(d));
    }

    @Transactional
    public DeviceDto recordHeartbeat(UUID orgId, UUID deviceId, Long latencyMs, String appVersion, Instant deviceTime) {
        Device d = devices.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFound("Device not found"));

        if (!d.getOrgId().equals(orgId)) {
            throw new OrgMismatch("Org mismatch");
        }

        d.setLastSeen(Instant.now());

        if (latencyMs != null) {
            d.setLastLatencyMs(latencyMs);
        }

        if (appVersion != null && !appVersion.isBlank()){
            d.setAppVersion(appVersion);
        }

        DeviceStatus prior = d.getStatus();
        DeviceStatus next;
        if (latencyMs != null && latencyMs > policy.degradedLatencyMs()) {
            next = DeviceStatus.DEGRADED;
        } else {
            next = DeviceStatus.ONLINE;
        }
        if (prior != next) {
            d.setStatus(next);
            devices.save(d);

            // Realtime push after state change: station-scoped topic reduces noise and enforces auth scopes at subscribe time
            realtimeGateway.sendDeviceStatus(d.getOrgId(), d.getStationId().toString(), Map.of(
                    "deviceId", d.getId().toString(),
                    "status", d.getStatus().name(),
                    "lastSeen", d.getLastSeen().toString(),
                    "latencyMs", d.getLastLatencyMs(),
                    "appVersion", d.getAppVersion()
            ));

            // TODO: audit.emit("ops.device.status_changed", {from: prior, to: next})

        } else {
            devices.save(d);
        }
        return deviceMapper.entityToDto(d);
    }
}