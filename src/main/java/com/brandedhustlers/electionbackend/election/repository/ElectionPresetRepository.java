package com.brandedhustlers.electionbackend.election.repository;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.election.domain.ElectionPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionPresetRepository extends JpaRepository<ElectionPreset, UUID> {

    /**
     * Find all non-deleted presets for an organization.
     */
    @Query("""
            select p from ElectionPreset p
            where p.organization.organizationId = :orgId
              and p.deleteYn = 'N'
            order by p.name asc
            """)
    List<ElectionPreset> findByOrgId(UUID orgId);

    /**
     * Find presets for org filtered by election type.
     */
    @Query("""
            select p from ElectionPreset p
            where p.organization.organizationId = :orgId
              and p.electionType.typeId = :typeId
              and p.deleteYn = 'N'
            """)
    List<ElectionPreset> findByOrgIdAndTypeId(UUID orgId, UUID typeId);

    Optional<ElectionPreset> findByPresetIdAndDeleteYn(UUID presetId, DeleteYn deleteYn);
}
