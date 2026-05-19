package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Query("select d from Device d where d.stationId = :stationId")
    List<Device> findByStationId(UUID stationId);

    @Query("select d from Device d where d.orgId = :orgId and d.status = :status")
    List<Device> findByOrgIdAndStatus(UUID orgId, DeviceStatus status);

    /**
     * DB-level filtered + paginated device listing. Replaces the DeviceService
     * pattern of findAll() + in-memory stream filters + PageImpl construction,
     * which loaded every device in every org into heap on each list request.
     * Null stationId / status params are handled via IS NULL OR checks so a
     * single method serves all filter combinations.
     */
    @Query("""
        SELECT d FROM Device d
        WHERE d.orgId = :orgId
          AND (:stationId IS NULL OR d.stationId = :stationId)
          AND (:status IS NULL OR d.status = :status)
        """)
    Page<Device> findByOrgIdFiltered(
        @Param("orgId") UUID orgId,
        @Param("stationId") UUID stationId,
        @Param("status") DeviceStatus status,
        Pageable pageable);

    /**
     * Returns only devices that are candidates for being flipped OFFLINE:
     * - not already OFFLINE (avoids re-processing rows that are already done)
     * - lastSeen is not null (newly enrolled devices are excluded)
     * - lastSeen is before the cutoff (haven't heartbeated recently enough)
     *
     * This replaces the findAll() + in-memory filter in DeviceMonitorImpl,
     * eliminating a full table scan on every hourly sweep.
     */
    @Query("select d from Device d where d.status <> :offline and d.lastSeen is not null and d.lastSeen < :cutoff")
    List<Device> findStaleDevices(@Param("offline") DeviceStatus offline, @Param("cutoff") Instant cutoff);
}
