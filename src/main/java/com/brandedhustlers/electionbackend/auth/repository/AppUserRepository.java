package com.brandedhustlers.electionbackend.auth.repository;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("select a from AppUser a where a.username = :username")
    Optional<AppUser> findByAppUserName(String username);

    @Query("select a from AppUser a where a.appUserEmail = :userEmail")
    Optional<AppUser> findByAppUserEmail(String userEmail);

    @Query("select a from AppUser a where a.appUserEmail = :query or a.username = :query")
    Optional<AppUser> findByUsernameOrEmail(String query);

    boolean existsByUsername(String username);

    @Query("select  a from AppUser a where a.appUserOrganization.organizationId = :orgId")
    List<AppUser> getUsersInOrg(UUID orgId);
}

