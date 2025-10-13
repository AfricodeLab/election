package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.dto.AddUserToOrg;
import com.brandedhustlers.electionbackend.auth.dto.CreateOrganizationRequest;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface IOrganizationService {
    OrganizationDTO createOrganization(CreateOrganizationRequest request);

    OrganizationDTO updateOrganization(UUID orgId, OrganizationDTO organizationDTO);

    Optional<OrganizationDTO> getOrganization(UUID orgId);

    Page<OrganizationDTO> listOrganizations(Pageable pageable);

    void addUserToOrganization(AddUserToOrg request);

    void deactivateOrganization(UUID orgId);
}
