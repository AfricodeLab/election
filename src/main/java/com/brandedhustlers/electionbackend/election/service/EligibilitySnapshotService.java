package com.brandedhustlers.electionbackend.election.service;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.voter.domain.EligibleVoterElection;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.EligibleVoterElectionRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import lombok.extern.slf4j.Slf4j;
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
     * Pre-compute eligible voter-election pairs for a launch.
     * Called when a launch transitions to ACTIVE.
     */
    @Transactional
    public int computeForLaunch(UUID orgId, UUID launchId, List<Election> elections) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        List<Voter> voters = voterRepository.findByOrganization(org);
        Timestamp now = Timestamp.from(Instant.now());

        List<EligibleVoterElection> batch = new ArrayList<>();

        for (Voter voter : voters) {
            for (Election election : elections) {
                boolean eligible;
                if (election.getEligibilityPolicy() == null) {
                    eligible = true;
                } else {
                    eligible = eligibilityService
                            .evaluate(voter.getAttributes(), election.getEligibilityPolicy())
                            .isAllowed();
                }

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

        eligibleVoterElectionRepository.saveAll(batch);
        log.info("Pre-computed {} eligible voter-election pairs for launch {}", batch.size(), launchId);
        return batch.size();
    }

    /**
     * Pre-compute eligible voter-election pairs for a single standalone election
     * (one brought live directly via POST /election/live/{id}, not via a Launch).
     *
     * Stored with launchId = null to distinguish from launch-scoped snapshots.
     * Called immediately before the voter notification is queued.
     */
    @Transactional
    public int computeForElection(UUID orgId, UUID electionId, Election election) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        List<Voter> voters = voterRepository.findByOrganization(org);
        Timestamp now = Timestamp.from(Instant.now());

        List<EligibleVoterElection> batch = new ArrayList<>();

        for (Voter voter : voters) {
            boolean eligible;
            if (election.getEligibilityPolicy() == null) {
                eligible = true;
            } else {
                eligible = eligibilityService
                        .evaluate(voter.getAttributes(), election.getEligibilityPolicy())
                        .isAllowed();
            }

            if (eligible) {
                batch.add(EligibleVoterElection.builder()
                        .voterId(voter.getVoterId())
                        .electionId(electionId)
                        .launchId(null)   // null = standalone, not part of a launch
                        .computedAt(now)
                        .build());
            }
        }

        eligibleVoterElectionRepository.saveAll(batch);
        log.info("Pre-computed {} eligible voter-election pairs for standalone election {}",
                batch.size(), electionId);
        return batch.size();
    }
}
