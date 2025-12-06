package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoterRepository extends JpaRepository<Voter, UUID> {

    @Query("select v from Voter v where v.organization.organizationId = :orgId and v.name = :q")
    List<Voter> findByOrgIDAndNameContainingIgnoreCase(UUID orgId, String q);

    @Query("select v from Voter v where v.voterId = :voterId and v.deleteYn = 'N' and v.organization.organizationId = :orgId")
    Voter findVoterById(UUID voterId, UUID orgId);

    @Query("select v from Voter v where v.organization = :organization")
    List<Voter> findByOrganization(Organization organization);


    @Query("select v from Voter v where v.voterId = :voterId and v.organization.organizationId = :organizationId")
    boolean existsByVoterId(UUID voterId, UUID organizationId);

    @Query("select v.voterId from Voter v where v.organization.organizationId = :organizationId")
    List<UUID> findAllByVoterIdIn(List<UUID> voterIds, UUID organizationId);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);

    Optional<Voter> findByOrganization_OrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    Optional<Voter> findByEmailIgnoreCase(String email);

    @Query("select v from Voter v where v.organization.organizationId = :orgId and v.deleteYn = 'N'")
    List<Voter> findByOrgId(UUID orgId);
}
