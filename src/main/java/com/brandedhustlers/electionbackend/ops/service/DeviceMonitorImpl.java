package com.brandedhustlers.electionbackend.ops.service;

/**
 * DeviceMonitorImpl: periodic OFFLINE detection when devices stop heartbeating beyond the threshold.
 * Justification: separates time-based policy from request-time handling; scalable batch processing with minimal locking.
 */

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.dto.OpsPolicy;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import com.brandedhustlers.electionbackend.ops.repository.DeviceRepository;
import com.brandedhustlers.electionbackend.realtime.gateway.RealtimeGateway;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeviceMonitorImpl implements DeviceMonitor {
    private final DeviceRepository devices;
    private final RealtimeGateway rt;

    DeviceMonitorImpl(DeviceRepository devices, RealtimeGateway rt) {
        this.devices = devices;
        this.rt = rt;
    }

    static OpsPolicy policy = new OpsPolicy(
            12233,
            12223,
            5
    );

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "1", timeUnit = TimeUnit.HOURS)
    public void sweepAndMarkOffline() {

        Instant cutoff = Instant.now().minusMillis(policy.offlineThresholdMs());

        // Targeted query: only non-OFFLINE devices whose lastSeen is before the cutoff.
        // Replaces findAll() + in-memory filter — avoids a full table scan each hour.
        List<Device> stale = devices.findStaleDevices(DeviceStatus.OFFLINE, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        for (Device d : stale) {
            d.setStatus(DeviceStatus.OFFLINE);
            rt.sendDeviceStatus(d.getOrgId(), d.getStationId().toString(), Map.of(
                    "deviceId", d.getId().toString(),
                    "status", d.getStatus().name(),
                    "lastSeen", d.getLastSeen().toString(),
                    "latencyMs", d.getLastLatencyMs(),
                    "appVersion", d.getAppVersion()
            ));
            // TODO: audit.emit("ops.device.status_changed", {from: prior, to: OFFLINE})
        }

        // Batch-flush all status changes in one round-trip instead of one save() per device.
        devices.saveAll(stale);
    }
}