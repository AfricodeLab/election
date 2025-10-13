package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.config.security.JWTService;
import com.brandedhustlers.electionbackend.common.exceptions.InvalidUpdate;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import com.brandedhustlers.electionbackend.election.dto.CreateElectionRequest;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import com.brandedhustlers.electionbackend.election.dto.UpdateElection;
import com.brandedhustlers.electionbackend.election.enums.ElectionStatus;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.mapper.ElectionMapper;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ElectionService implements IElectionService{
    private final ElectionRepository electionRepository;
    private final HttpServletRequest request;
    private final JWTService jwtService;
    private final AppUserRepository appUserRepository;
    private final HttpServletRequest httpServletRequest;
    private final IElectionService iElectionService;
    private final ElectionMapper electionMapper;

    public ElectionService(ElectionRepository electionRepository,
                           HttpServletRequest request,
                           JWTService jwtService,
                           AppUserRepository appUserRepository, HttpServletRequest httpServletRequest, IElectionService iElectionService, ElectionMapper electionMapper) {
        this.electionRepository = electionRepository;
        this.request = request;
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.httpServletRequest = httpServletRequest;
        this.iElectionService = iElectionService;
        this.electionMapper = electionMapper;
    }
    private UUID getAppUserId(HttpServletRequest request){
        return jwtService.extractAppUserId(request);
    }

    @Override
    public ElectionDTO createElection(CreateElectionRequest request) {
        AppUser creator = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User account not found"));
        //todo implement a check for user's role before access

        Organization electionOrg = creator.getAppUserOrganization();
        Election newElection = Election.builder()
                .electionTitle(request.electionTitle())
                .electionDescription(request.electionDescription())
                .electionType(request.electionType())
                .electionStatus(ElectionStatus.DRAFT)
                .electionStartTime(request.startTime())
                .electionEndTime(request.endtime())
                .expectedVoterCount(request.expectedVoterCount())
                .isPubliclyVisible(request.isPubliclyVisible())
                .shouldEndAutomatically(request.shouldEndAutomatically())
                .organization(electionOrg)
                .createdBy(creator)
                .build();
        return electionMapper.entityToDto(
                electionRepository.save(newElection)
        );
    }

    @Override
    public ElectionDTO updateElection(UUID electionID, UpdateElection request) {
        AppUser updatingUser = appUserRepository.findById(getAppUserId(httpServletRequest))
                .orElseThrow(() -> new UserNotFound("User not found"));

        Election electionToUpdate = electionRepository.findById(electionID)
                .orElseThrow(() -> new ElectionNotFound("An election with this ID: " + electionID + " does not exist"));

        Set<PermissionType> userPermissions = updatingUser.getRoles()
                .stream()
                .flatMap(role -> role.getRolePermissions().stream())
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());

        if(!userPermissions.contains(PermissionType.EDIT_ELECTIONS)){
            throw new InvalidUpdate("You do not have the authority to edit an election");
        }

        if(!electionToUpdate.getElectionStatus().equals(ElectionStatus.DRAFT)){
            throw new InvalidUpdate("The selected election is not in draft and can no longer be updated");
        }
        //update can happen now all checks have been made
        
        return null;
    }

    @Override
    public ElectionDTO changeElectionStatus(UUID electionId, ElectionStatus newStatus) {
        return null;
    }

    @Override
    public ElectionDTO addCandidate(UUID electionId, CandidateDTO newCandidate) {
        return null;
    }
}
