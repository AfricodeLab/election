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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Page<TallyDto> get(UUID electionId, Pageable pageable) {
        AppUser actor = getUser.getCurrentAuthenticatedUser();
        util.requireAdminOrPermission(actor, PermissionType.VIEW_RESULTS);

        // DB-level pagination: only the requested page of tallies is fetched.
        // Previously all tallies were loaded into memory then sliced with subList().
        Page<Tally> tallyPage = repo.findByIdElectionId(electionId, pageable);

        // Batch-load only the candidates referenced by this page's tallies
        // (one IN query instead of N individual findById calls).
        Set<UUID> candidateIds = tallyPage.getContent().stream()
                .map(t -> t.getId().getCandidateId())
                .collect(Collectors.toSet());

        // Election title and candidates are fetched in the same @Transactional
        // context as the tally query — one DB connection, three queries total.
        String electionTitle = electionRepository.getElectionTitleById(electionId);
        Map<UUID, Candidate> candidateMap = candidateRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(Candidate::getCandidateId, c -> c));

        return tallyPage.map(tally -> new TallyDto(
                tally.getId().getElectionId(),
                electionTitle,
                candidateMapper.entityToDTO(candidateMap.get(tally.getId().getCandidateId())),
                tally.getCount(),
                tally.getUpdatedAt()
        ));
    }
}
