package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LaunchRepository extends JpaRepository<Launch, UUID> {
    @Query("select l from Launch l where l.orgId = :orgId and l.status = :status and l.deleteYn = 'N'")
    List<Launch> findByOrgIdAndStatus(UUID orgId, LaunchStatus status);

    @Query("select l from Launch l where l.id = :launchId and l.deleteYn = 'N'")
    Optional<Launch> findByLaunchId(UUID launchId);

    List<Launch> findAllLaunches();
}
