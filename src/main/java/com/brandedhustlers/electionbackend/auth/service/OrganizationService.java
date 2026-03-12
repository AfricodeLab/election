package com.brandedhustlers.electionbackend.auth.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.UserOrganization;
import com.brandedhustlers.electionbackend.auth.dto.AddUserToOrg;
import com.brandedhustlers.electionbackend.auth.dto.CreateOrganizationRequest;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.exceptions.ConflictException;
import com.brandedhustlers.electionbackend.auth.exceptions.OrganizationNotFound;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.mapper.OrganizationMapper;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.auth.repository.UserOrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganizationService implements IOrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final AppUserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMapper organizationMapper,
                               AppUserRepository userRepository,
                               UserOrganizationRepository userOrganizationRepository) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
    }

    @Override
    @Transactional
    public OrganizationDTO createOrganization(CreateOrganizationRequest request, UUID creatorId) {
        AppUser createdBy = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFound("Creator account not found and hence new organization cannot be created."));

        Optional<Organization> potentialConflict = organizationRepository.findByName(request.organizationName());
        if (potentialConflict.isPresent()) {
            throw new ConflictException("An organization with this name: " + request.organizationName() + " already exists.");
        }
        Organization newOrg = Organization.builder()
                .organizationName(request.organizationName())
                .organizationDescription(request.organizationDescription())
                .deleteYn(DeleteYn.N)
                .isEnabled(true)
                .createdBy(createdBy)
                .createdAt(Timestamp.from(Instant.now()))
                .build();

        Organization saved = organizationRepository.save(newOrg);

        // Deactivate any current active org, then add creator as ORG_ADMIN of the new org
        userOrganizationRepository.deactivateAllForUser(creatorId);
        userOrganizationRepository.save(UserOrganization.builder()
                .appUser(createdBy)
                .organization(saved)
                .roleInOrg(createdBy.getUserRole().name())
                .activeOrg(true)
                .build());

        return organizationMapper.entityToDTO(saved);
    }

    @Override
    public OrganizationDTO updateOrganization(UUID orgId, OrganizationDTO organizationDTO) {
        Organization orgToUpdate = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + orgId + " not found."));

        Optional<Organization> potentialConflict = organizationRepository.findByName(organizationDTO.organizationName());
        if (potentialConflict.isPresent()) {
            throw new ConflictException("An organization with this name: " + organizationDTO.organizationName() + " already exists.");
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
    @Transactional
    public void addUserToOrganization(AddUserToOrg request) {
        Organization organization = organizationRepository.findById(request.orgId())
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + request.orgId() + " not found."));

        AppUser user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFound("User with ID: " + request.userId() + " not found"));

        // Check if already a member
        if (userOrganizationRepository.findByAppUser_AppUserIdAndOrganization_OrganizationId(
                request.userId(), request.orgId()).isPresent()) {
            throw new ConflictException("User is already a member of this organization");
        }

        userOrganizationRepository.save(UserOrganization.builder()
                .appUser(user)
                .organization(organization)
                .roleInOrg(user.getUserRole().name())
                .activeOrg(false)
                .build());
    }

    @Override
    public void deactivateOrganization(UUID orgId) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFound("Organization with ID: " + orgId + " not found."));

        organization.setEnabled(false);
        organizationRepository.save(organization);
    }

    @Transactional
    public void switchActiveOrg(UUID userId, UUID orgId) {
        userOrganizationRepository.findByAppUser_AppUserIdAndOrganization_OrganizationId(userId, orgId)
                .orElseThrow(() -> new OrganizationNotFound("You are not a member of this organization"));

        userOrganizationRepository.deactivateAllForUser(userId);

        UserOrganization membership = userOrganizationRepository
                .findByAppUser_AppUserIdAndOrganization_OrganizationId(userId, orgId)
                .orElseThrow(() -> new OrganizationNotFound("You are not a member of this organization"));

        membership.setActiveOrg(true);
        userOrganizationRepository.save(membership);
    }

    public List<OrganizationDTO> getUserOrganizations(UUID userId) {
        return userOrganizationRepository.findByAppUser_AppUserId(userId)
                .stream()
                .map(uo -> organizationMapper.entityToDTO(uo.getOrganization()))
                .toList();
    }
}
