package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionRepository extends JpaRepository<Election, UUID> {

    boolean existsByElectionTitleAndOrganization(String s, Organization appUserOrganization);

    @Query("select e from Election e where e.organization.organizationId = :organizationId")
    List<Election> findByOrganizationId(UUID organizationId);

    @Query("select e.electionTitle from Election e where e.electionId = :electionId")
    String getElectionTitleById(UUID electionId);

    boolean existsByElectionIdAndOrganization_OrganizationId(UUID electionId, UUID organizationOrganizationId);

    @Query("""
              select e from Election e
              where e.organization.organizationId = :orgId
                and e.electionStatus = :status
                and e.electionStartTime <= :now
                and e.electionEndTime >= :now
            """)
    List<Election> findLiveByOrg(@Param("orgId") UUID orgId,
                                 @Param("status") ElectionStatus status,
                                 @Param("now") Timestamp now);

    @Query("select e.organization.organizationId from Election e where e.electionId = :electionId")
    UUID findOrgId(UUID electionId);

    @Query("""
               select e from Election e
               where e.electionStatus = 'LIVE'
            """)
    List<Election> findLive(Timestamp now);

//    @Query("""
//              select e from Election e
//              join ElectionVoter ev on ev.id.electionId = e.electionId
//              where e.organization.organizationId = :orgId
//                and e.electionStatus = :status
//                and e.electionStartTime <= :now
//                and e.electionEndTime >= :now
//                and ev.id.voterId = :voterId
//            """)
//    List<Election> findLiveByOrgAndAssigned(@Param("orgId") UUID orgId,
//                                            @Param("voterId") UUID voterId,
//                                            @Param("status") ElectionStatus status,
//                                            @Param("now") Timestamp now);


    //remove timestamp checks for now
    @Query("""
              select e from Election e
              where e.organization.organizationId = :orgId
                and e.electionStatus = :status
            """)
    List<Election> findLiveByOrgAndAssigned(@Param("orgId") UUID orgId,
                                            @Param("status") ElectionStatus status,
                                            @Param("now") Timestamp now);

    @Query("""
    SELECT e FROM Election e
    WHERE e.organization.organizationId = :orgId
      AND e.electionStatus = :status
      AND NOT EXISTS (
        SELECT 1 FROM ElectionVoter ev
        WHERE ev.election.electionId = e.electionId
          AND ev.voter.voterId = :voterId
          AND ev.hasVoted = true
      )
""")
    List<Election> findLiveByOrgAndNotVoted(
            @Param("orgId") UUID orgId,
            @Param("status") ElectionStatus status,
            @Param("voterId") UUID voterId
    );

/*    @Query("""
              select e from Election e
              where e.electionId = :electionId
                and e.organization.organizationId = :orgId
                and e.electionStatus = :status
                and e.electionStartTime <= :now
                and e.electionEndTime >= :now
                and e.deleteYn = 'N'
            """)
    Optional<Election> verifyLiveInOrg(@Param("electionId") UUID electionId,
                                       @Param("orgId") UUID orgId,
                                       @Param("status") ElectionStatus status,
                                       @Param("now") Timestamp now);*/

    @Query("""
              select e from Election e
              where e.electionId = :electionId
                and e.organization.organizationId = :orgId
                and e.electionStatus = :status
            """)
    Optional<Election> verifyLiveInOrg(@Param("electionId") UUID electionId,
                                       @Param("orgId") UUID orgId,
                                       @Param("status") ElectionStatus status,
                                       @Param("now") Timestamp now);

    @Query("select e from Election e where e.electionId = :electionId and e.deleteYn = 'N'")
    Optional<Election> findByElectionId(UUID electionId);

    @Query("select e.organization from Election e where e.electionId = :electionId")
    Organization getElectionOrganization(UUID electionId);
}
