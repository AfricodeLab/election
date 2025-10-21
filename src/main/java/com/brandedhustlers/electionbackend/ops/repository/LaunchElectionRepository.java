package com.brandedhustlers.electionbackend.ops.repository;

import com.brandedhustlers.electionbackend.ops.domain.LaunchElection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LaunchElectionRepository extends JpaRepository<LaunchElection, UUID> {
    @Query("select l from LaunchElection l where l.id.launchId = :launchId")
    List<LaunchElection> findByIdLaunchId(UUID launchId);
}
