package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.enums.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    @Query("select d from Device d where d.stationId = :stationId")
    List<Device> findByStationId(UUID stationId);

    @Query("select d from Device d where d.orgId = :orgId and d.status = :status")
    List<Device> findByOrgIdAndStatus(UUID orgId, DeviceStatus status);
}
