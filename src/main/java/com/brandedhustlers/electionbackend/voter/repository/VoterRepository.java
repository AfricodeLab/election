package com.brandedhustlers.electionbackend.voter.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoterRepository extends JpaRepository<Voter, UUID> {

    // Substring search — LOWER(...) LIKE LOWER(CONCAT('%',:q,'%')) is case-insensitive
    // on all collations and avoids a separate index scan for exact equality.
    @Query("select v from Voter v where v.organization.organizationId = :orgId and LOWER(v.name) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Voter> findByOrgIDAndNameContainingIgnoreCase(UUID orgId, String q);

    @Query("select v from Voter v where v.voterId = :voterId and v.deleteYn = 'N' and v.organization.organizationId = :orgId")
    Voter findVoterById(UUID voterId, UUID orgId);

    @Query("select v from Voter v where v.voterId = :voterId and v.deleteYn = 'N' and v.organization.organizationId = :orgId")
    Optional<Voter> findVoterByIdAndOrgId(UUID voterId, UUID orgId);

    // Non-paginated — kept for small orgs / backward compat.
    @Query("select v from Voter v where v.organization = :organization")
    List<Voter> findByOrganization(Organization organization);

    // Paginated overload used by EligibilitySnapshotService to stream voters
    // in pages of 1 000 instead of loading the entire org roster into heap.
    @Query(value = "select v from Voter v where v.organization.organizationId = :orgId",
           countQuery = "select count(v) from Voter v where v.organization.organizationId = :orgId")
    Page<Voter> findByOrgIdPaged(@Param("orgId") UUID orgId, Pageable pageable);


    @Query("select v from Voter v where v.voterId = :voterId and v.organization.organizationId = :organizationId")
    boolean existsByVoterId(UUID voterId, UUID organizationId);

    @Query("select v from Voter v where v.voterId in :voterIds and v.organization.organizationId = :organizationId and v.deleteYn = 'N'")
    List<Voter> findAllByVoterIdIn(@Param("voterIds") List<UUID> voterIds, @Param("organizationId") UUID organizationId);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);

    Optional<Voter> findByOrganization_OrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    Optional<Voter> findByEmailIgnoreCase(String email);

    Optional<Voter> findByPhone(String phone);

    // Non-paginated overload — kept for the small in-memory uses (eligibility snapshot,
    // voter assignment validation). For the paginated list API use the Pageable overload below.
    @Query("select v from Voter v where v.organization.organizationId = :orgId and v.deleteYn = 'N'")
    List<Voter> findByOrgId(UUID orgId);

    // DB-level pagination: LIMIT/OFFSET pushed to Postgres rather than loading
    // all voters into Java memory and slicing with subList().
    @Query(value = "select v from Voter v where v.organization.organizationId = :orgId and v.deleteYn = 'N'",
           countQuery = "select count(v) from Voter v where v.organization.organizationId = :orgId and v.deleteYn = 'N'")
    Page<Voter> findByOrgId(UUID orgId, Pageable pageable);
}
