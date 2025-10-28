package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.voter.domain.VoterImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoterImportJobRepository extends JpaRepository<VoterImportJob, UUID> {
    List<VoterImportJob> findByOrgIdOrderByStartedAtDesc(UUID orgId);
}
