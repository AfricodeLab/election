package com.brandedhustlers.electionbackend.voting.repository;

import com.brandedhustlers.electionbackend.voting.domain.VoteArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoteArchiveRepository extends JpaRepository<VoteArchive, UUID> {
}
