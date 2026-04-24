package com.brandedhustlers.electionbackend.auth.repository;

import com.brandedhustlers.electionbackend.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    @Query("select r from Role r where r.roleName = :roleName")
    Optional<Role> findByRoleName(String roleName);

    /** All system roles (seeded by Branded Hustlers, shared across all orgs). */
    List<Role> findByOrgIdIsNull();

    /** All custom roles scoped to a specific organization. */
    List<Role> findByOrgId(UUID orgId);

    /** System roles + custom roles visible to an org. */
    @Query("select r from Role r where r.orgId is null or r.orgId = :orgId")
    List<Role> findSystemAndOrgRoles(UUID orgId);

    boolean existsByRoleNameAndOrgId(String roleName, UUID orgId);
}
