package com.brandedhustlers.electionbackend.results.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.common.util.Util;
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
    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;
    private final ElectionRepository electionRepository;
    private final GetUser getUser;
    private final Util util;

    public ResultsService(TallyRepository repo,
                          CandidateRepository candidateRepository,
                          CandidateMapper candidateMapper,
                          ElectionRepository electionRepository,
                          GetUser getUser,
                          Util util) {
        this.repo = repo;
        this.electionRepository = electionRepository;
        this.getUser = getUser;
        this.candidateRepository = candidateRepository;
        this.candidateMapper = candidateMapper;
        this.util = util;
    }

    public Page<TallyDto> get(UUID electionId, Pageable pageable) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();

        util.requireAdminOrPermission(actor, PermissionType.VIEW_RESULTS);

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
}
