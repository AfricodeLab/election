package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    // Returns Page<Station> so Spring Data auto-generates the COUNT(*) query,
    // giving an accurate total for pagination metadata.
    @Query("select s from Station s where s.orgId = :orgId and s.launchId = :launchId")
    Page<Station> findByOrgIdAndLaunchId(UUID orgId, UUID launchId, Pageable pageable);
}
