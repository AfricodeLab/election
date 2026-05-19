package com.brandedhustlers.electionbackend.auth.repository;

import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionType(PermissionType type);

    /**
     * Bulk-delete permissions whose type is no longer in the current enum.
     * Replaces the findAll() + per-row delete() loop in PermissionSyncer,
     * which issued N individual DELETEs (each potentially touching the join table).
     * This issues a single DELETE … WHERE … NOT IN (…) statement.
     */
    @Modifying
    @Query("DELETE FROM Permission p WHERE p.permissionType NOT IN :activeTypes")
    void deleteObsoletePermissions(@Param("activeTypes") Set<PermissionType> activeTypes);
}

