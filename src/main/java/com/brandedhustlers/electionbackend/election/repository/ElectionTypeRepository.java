package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.election.domain.ElectionTypeEntity;
import com.brandedhustlers.electionbackend.election.dto.ElectionTypeOptionDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionTypeRepository extends JpaRepository<ElectionTypeEntity, UUID> {

    /**
     * Find all election types visible to an org: system-level types + org's own
     * types.
     */
    @Query("""
            select t from ElectionTypeEntity t
            where t.deleteYn = 'N'
              and (t.isSystem = true or t.organization.organizationId = :orgId)
            order by t.isSystem desc, t.name asc
            """)
    List<ElectionTypeEntity> findVisibleToOrg(UUID orgId);

    /**
     * Find all system-level types (built-in, non-deletable).
     */
    List<ElectionTypeEntity> findAllByIsSystemTrueAndDeleteYn(
            com.brandedhustlers.electionbackend.auth.enums.DeleteYn deleteYn);

    boolean existsByNameAndOrganization(String name, Organization organization);

    @Query("select e from ElectionTypeEntity e where e.typeId = :typeId and e.deleteYn = 'N'")
    Optional<ElectionTypeEntity> findByElectionTypeId(UUID typeId);

    @Query("select e.typeId, e.name from ElectionTypeEntity e where e.deleteYn = 'N'")
    List<ElectionTypeOptionDto> getElectionTypeOption();

    @Query("select e.typeId, e.name from ElectionTypeEntity e where e.deleteYn = 'N' and e.organization.organizationId = :orgId")
    List<ElectionTypeOptionDto> getElectionTypeOptionByOrg(UUID orgId);
}
