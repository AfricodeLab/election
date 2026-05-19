package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.voter.domain.EligibleVoterElection;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.EligibleVoterElectionRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class EligibilitySnapshotService {

    private final EligibilityService eligibilityService;
    private final VoterRepository voterRepository;
    private final OrganizationRepository organizationRepository;
    private final EligibleVoterElectionRepository eligibleVoterElectionRepository;

    public EligibilitySnapshotService(EligibilityService eligibilityService,
                                      VoterRepository voterRepository,
                                      OrganizationRepository organizationRepository,
                                      EligibleVoterElectionRepository eligibleVoterElectionRepository) {
        this.eligibilityService = eligibilityService;
        this.voterRepository = voterRepository;
        this.organizationRepository = organizationRepository;
        this.eligibleVoterElectionRepository = eligibleVoterElectionRepository;
    }

    /**
     * Voters are processed in pages of this size to avoid loading the full roster into heap.
     */
    private static final int VOTER_PAGE_SIZE = 1_000;

    /**
     * Pre-compute eligible voter-election pairs for a launch.
     * Called when a launch transitions to ACTIVE.
     * <p>
     * Voters are streamed in pages of {@value VOTER_PAGE_SIZE} rather than
     * loading the entire org roster into JVM memory at once. For an org with
     * 100 000 voters this reduces peak heap use from ~100 MB to ~1 MB per page.
     */
    @Transactional
    public int computeForLaunch(UUID orgId, UUID launchId, List<Election> elections) {
        Timestamp now = Timestamp.from(Instant.now());
        int total = 0;
        int page = 0;
        Page<Voter> voterPage;

        do {
            voterPage = voterRepository.findByOrgIdPaged(orgId, PageRequest.of(page, VOTER_PAGE_SIZE));
            List<EligibleVoterElection> batch = new ArrayList<>();

            for (Voter voter : voterPage.getContent()) {
                for (Election election : elections) {
                    boolean eligible = election.getEligibilityPolicy() == null
                            || eligibilityService
                            .evaluate(voter.getAttributes(), election.getEligibilityPolicy())
                            .isAllowed();
                    if (eligible) {
                        batch.add(EligibleVoterElection.builder()
                                .voterId(voter.getVoterId())
                                .electionId(election.getElectionId())
                                .launchId(launchId)
                                .computedAt(now)
                                .build());
                    }
                }
            }

            if (!batch.isEmpty()) {
                eligibleVoterElectionRepository.saveAll(batch);
                total += batch.size();
            }
            page++;
        } while (voterPage.hasNext());

        log.info("Pre-computed {} eligible voter-election pairs for launch {}", total, launchId);
        return total;
    }

    /**
     * Pre-compute eligible voter-election pairs for a single standalone election
     * (one brought live directly via POST /election/live/{id}, not via a Launch).
     * <p>
     * Stored with launchId = null to distinguish from launch-scoped snapshots.
     * Called immediately before the voter notification is queued.
     * Voters are streamed in pages of {@value VOTER_PAGE_SIZE}.
     */
    @Transactional
    public int computeForElection(UUID orgId, UUID electionId, Election election) {
        Timestamp now = Timestamp.from(Instant.now());
        int total = 0;
        int page = 0;
        Page<Voter> voterPage;

        do {
            voterPage = voterRepository.findByOrgIdPaged(orgId, PageRequest.of(page, VOTER_PAGE_SIZE));
            List<EligibleVoterElection> batch = new ArrayList<>();

            for (Voter voter : voterPage.getContent()) {
                boolean eligible = election.getEligibilityPolicy() == null
                        || eligibilityService
                        .evaluate(voter.getAttributes(), election.getEligibilityPolicy())
                        .isAllowed();
                if (eligible) {
                    batch.add(EligibleVoterElection.builder()
                            .voterId(voter.getVoterId())
                            .electionId(electionId)
                            .launchId(null)   // null = standalone, not part of a launch
                            .computedAt(now)
                            .build());
                }
            }

            if (!batch.isEmpty()) {
                eligibleVoterElectionRepository.saveAll(batch);
                total += batch.size();
            }
            page++;
        } while (voterPage.hasNext());

        log.info("Pre-computed {} eligible voter-election pairs for standalone election {}",
                total, electionId);
        return total;
    }
}
