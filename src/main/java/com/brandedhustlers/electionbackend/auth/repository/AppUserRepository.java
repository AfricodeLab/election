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

    /**
     * Used by CustomUserDetailsService on every authenticated request.
     * JOIN FETCH loads roles → permissions and userOrganizations → organization
     * in a single SQL query (two joins on Set collections — no MultipleBagFetchException).
     * DISTINCT prevents Hibernate returning duplicate AppUser rows from the Cartesian join.
     */
    @Query("""
        SELECT DISTINCT u FROM AppUser u
        LEFT JOIN FETCH u.roles r
        LEFT JOIN FETCH r.rolePermissions
        LEFT JOIN FETCH u.userOrganizations uo
        LEFT JOIN FETCH uo.organization
        WHERE u.appUserEmail = :query OR u.username = :query
        """)
    Optional<AppUser> findByUsernameOrEmail(String query);

    boolean existsByUsername(String username);

    Optional<AppUser> findByEmailVerificationToken(String token);

    Optional<AppUser> findByPasswordResetToken(String token);

    @Query("select uo.appUser from UserOrganization uo where uo.organization.organizationId = :orgId")
    List<AppUser> getUsersInOrg(UUID orgId);
}

