package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.dto.AddUserToOrg;
import com.brandedhustlers.electionbackend.auth.dto.CreateOrganizationRequest;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.auth.exceptions.ConflictException;
import com.brandedhustlers.electionbackend.auth.exceptions.OrganizationNotFound;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationService implements IOrganizationService {
    private final JWTService jwtService;
    private final OrganizationRepository organizationRepository;
    private final HttpServletRequest servletRequest;
    private final OrganizationMapper organizationMapper;
    private final AppUserRepository userRepository;

    public OrganizationService(JWTService jwtService,
                               OrganizationRepository organizationRepository,
                               HttpServletRequest servletRequest,
                               OrganizationMapper organizationMapper,
                               AppUserRepository userRepository) {
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
        this.servletRequest = servletRequest;
        this.organizationMapper = organizationMapper;
        this.userRepository = userRepository;
    }

    @Override
    public OrganizationDTO createOrganization(CreateOrganizationRequest request) {
        Optional<Organization> potentialConflict =  organizationRepository.findByName(request.organizationName());
        if(potentialConflict.isPresent()){
            throw new ConflictException("An organization with this name: " + request.organizationName() + " already exists.");
        }
        Organization newOrg = Organization.builder()
                .organizationName(request.organizationName())
                .organizationDescription(request.organizationDescription())
                .build();

        return organizationMapper.entityToDTO(
                organizationRepository.save(newOrg)
        );
    }

    @Override
    public OrganizationDTO updateOrganization(UUID orgId, OrganizationDTO organizationDTO) {
        Organization orgToUpdate = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + orgId + " not found."));

        //only the org's name can be updated
        Optional<Organization> potentialConflict = organizationRepository.findByName(organizationDTO.organizationName());
        if (potentialConflict.isPresent()) {
            throw new ConflictException("An organization with this name: " + organizationDTO.organizationName() + " already exisits.");
        }
        orgToUpdate.setOrganizationName(organizationDTO.organizationName());

        return organizationMapper.entityToDTO(
                organizationRepository.save(orgToUpdate)
        );
    }

    @Override
    public Optional<OrganizationDTO> getOrganization(UUID orgId) {
        return organizationRepository
                .findById(orgId)
                .map(organizationMapper::entityToDTO);
    }

    @Override
    public Page<OrganizationDTO> listOrganizations(Pageable pageable) {
        List<OrganizationDTO> organizationDTOS = organizationRepository.listAllOrganizations()
                .stream()
                .map(organizationMapper::entityToDTO)
                .toList();

        return new PageImpl<>(organizationDTOS, pageable, organizationDTOS.size());
    }

    @Override
    public void addUserToOrganization(AddUserToOrg request) {
        Organization organization = organizationRepository.findById(request.orgId())
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + request.orgId() + " not found."));

        AppUser user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFound("User with ID: " + request.userId() + " not found"));

        user.setAppUserOrganization(organization);
        userRepository.save(user);
        organizationRepository.save(organization);
    }

    @Override
    public void deactivateOrganization(UUID orgId) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + orgId + " not found."));

        organization.setEnabled(false);
        organizationRepository.save(organization);
    }
}
