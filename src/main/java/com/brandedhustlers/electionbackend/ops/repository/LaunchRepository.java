package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.enums.LaunchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LaunchRepository extends JpaRepository<Launch, UUID> {
    @Query("select l from Launch l where l.orgId = :orgId and l.status = :status")
    List<Launch> findByOrgIdAndStatus(UUID orgId, LaunchStatus status);
}
