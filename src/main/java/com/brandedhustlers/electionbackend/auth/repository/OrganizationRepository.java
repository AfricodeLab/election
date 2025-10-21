package com.brandedhustlers.electionbackend.auth.repository;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    @Query("select o from Organization o where o.organizationName = :orgName and o.deleteYn = 'N'")
    Optional<Organization> findByName(String orgName);

    @Query("select o from Organization o where o.deleteYn = 'N'")
    List<Organization> listAllOrganizations();


    Optional<Organization> findByOrganizationName(String organizationName);
}
