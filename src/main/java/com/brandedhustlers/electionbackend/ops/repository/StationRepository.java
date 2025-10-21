package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Station;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    @Query("select s from Station s where s.orgId = :orgId and s.launchId = :launchId")
    List<Station> findByOrgIdAndLaunchId(UUID orgId, UUID launchId, Pageable pageable);
}
