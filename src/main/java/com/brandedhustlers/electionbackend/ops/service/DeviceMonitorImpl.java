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
        // Minimal approach: stream through devices and flip those past cutoff; for large fleets, add a repo query with WHERE last_seen < cutoff AND status <> OFFLINE
        for (Device d : devices.findAll()) {

            if (d.getLastSeen() == null){
                continue; // newly enrolled, let first heartbeat decide
            }
            if (d.getStatus() != DeviceStatus.OFFLINE && d.getLastSeen().isBefore(cutoff)) {

                d.setStatus(DeviceStatus.OFFLINE);
                devices.save(d);
                rt.sendDeviceStatus(d.getStationId().toString(), Map.of(
                        "deviceId", d.getId().toString(),
                        "status", d.getStatus().name(),
                        "lastSeen", d.getLastSeen().toString(),
                        "latencyMs", d.getLastLatencyMs(),
                        "appVersion", d.getAppVersion()
                ));

                // TODO: audit.emit("ops.device.status_changed", {from: prior, to: OFFLINE})
            }
        }
    }
}