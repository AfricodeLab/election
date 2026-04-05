package com.brandedhustlers.electionbackend.common.flags;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgFeatureOverrideRepository extends JpaRepository<OrgFeatureOverride, UUID> {

    List<OrgFeatureOverride> findAllByOrgId(UUID orgId);

    Optional<OrgFeatureOverride> findByOrgIdAndFeature(UUID orgId, Feature feature);

    void deleteByOrgIdAndFeature(UUID orgId, Feature feature);
}
