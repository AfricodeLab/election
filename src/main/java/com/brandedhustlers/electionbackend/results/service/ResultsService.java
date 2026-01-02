package com.brandedhustlers.electionbackend.results.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Permission;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.common.exceptions.AccessDeniedException;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.mapper.CandidateMapper;
import com.brandedhustlers.electionbackend.election.repository.CandidateRepository;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.dto.TallyDto;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResultsService {
    private final TallyRepository repo;
    private final AppUserRepository appUserRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;
    private final ElectionRepository electionRepository;
    private final GetUser getUser;

    public ResultsService(TallyRepository repo,
                          AppUserRepository appUserRepository,
                          CandidateRepository candidateRepository,
                          CandidateMapper candidateMapper, ElectionRepository electionRepository,
                          GetUser getUser) {
        this.repo = repo;
        this.appUserRepository = appUserRepository;
        this.electionRepository = electionRepository;
        this.getUser = getUser;
        this.candidateRepository = candidateRepository;
        this.candidateMapper = candidateMapper;
    }

    public Page<TallyDto> get(UUID electionId, Pageable pageable) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        requireAdminOrPermission(actor, PermissionType.VIEW_RESULTS);

        List<Tally> tallies = repo.findByIdElectionId(electionId);

        Set<UUID> candidateIds = tallies.stream()
                .map(t -> t.getId().getCandidateId())
                .collect(Collectors.toSet());

        String electionTitle = electionRepository.getElectionTitleById(electionId);

        List<Candidate> candidates = candidateRepository.findAllById(candidateIds);

        Map<UUID, Candidate> candidateMap = candidates.stream()
                .collect(Collectors.toMap(Candidate::getCandidateId, c -> c));

        List<TallyDto> tallyDtos = tallies.stream()
                .map(tally -> {
                    Candidate candidate = candidateMap.get(tally.getId().getCandidateId());
                    return new TallyDto(
                            tally.getId().getElectionId(),
                            electionTitle,
                            candidateMapper.entityToDTO(candidate),
                            tally.getCount(),
                            tally.getUpdatedAt()
                    );
                })
                .toList();
        return new PageImpl<>(tallyDtos, pageable, tallyDtos.size());
    }

    private boolean authorityCheck(UUID appUserId, PermissionType allowedPermission) {
        AppUser updatingUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new UserNotFound("User not found"));

        Set<PermissionType> userPermissions = updatingUser.getRoles()
                .stream()
                .flatMap(role -> role.getRolePermissions().stream())
                .map(Permission::getPermissionType)
                .collect(Collectors.toSet());

        return userPermissions.contains(allowedPermission);
    }

    private boolean authorizeUser(AppUser user, UserRole userType) {
        return user.getUserRole().equals(userType);
    }

    private void requireAdminOrPermission(AppUser user, PermissionType permission) {
        boolean hasRole = authorizeUser(user, UserRole.ADMIN) || authorizeUser(user, UserRole.SUPER_ADMIN);
        boolean hasPermission = authorityCheck(user.getAppUserId(), permission);

        if (!hasRole && !hasPermission)
            throw new AccessDeniedException("You lack sufficient privileges for this action");
    }
}

//todo,