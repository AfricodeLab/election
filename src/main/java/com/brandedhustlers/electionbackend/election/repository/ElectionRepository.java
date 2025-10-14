package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.election.domain.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ElectionRepository extends JpaRepository<Election, UUID> {

    boolean existsByElectionTitleAndOrganization(String s, Organization appUserOrganization);
}
