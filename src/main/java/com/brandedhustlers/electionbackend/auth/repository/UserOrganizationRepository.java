package com.brandedhustlers.electionbackend.auth.repository;

import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOrganizationRepository extends JpaRepository<UserOrganization, UUID> {

    List<UserOrganization> findByAppUser_AppUserId(UUID userId);

    Optional<UserOrganization> findByAppUser_AppUserIdAndActiveOrgTrue(UUID userId);

    List<UserOrganization> findByOrganization_OrganizationId(UUID orgId);

    Optional<UserOrganization> findByAppUser_AppUserIdAndOrganization_OrganizationId(UUID userId, UUID orgId);

    @Modifying
    @Query("UPDATE UserOrganization uo SET uo.activeOrg = false WHERE uo.appUser.appUserId = :userId")
    void deactivateAllForUser(UUID userId);
}
