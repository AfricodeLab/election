package com.brandedhustlers.electionbackend.election.scheduler;

import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.election.service.IElectionService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Periodic scheduler that automatically closes LIVE elections when one of two
 * conditions is met:
 *
 * <ol>
 *   <li><strong>Time-based expiry</strong> — {@code electionEndTime} is in the past.</li>
 *   <li><strong>Vote-count-based expiry</strong> — {@code shouldEndAutomatically = true} and
 *       {@code totalVotes >= expectedVoterCount} (full participation reached).</li>
 * </ol>
 *
 * <p>Runs every 60 seconds. Uses ShedLock to guarantee that only one node in a
 * clustered deployment executes the sweep at a time.</p>
 */
@Slf4j
@Component
public class ElectionAutoCloseScheduler {

    private final ElectionRepository electionRepository;
    private final IElectionService electionService;

    public ElectionAutoCloseScheduler(ElectionRepository electionRepository,
                                      IElectionService electionService) {
        this.electionRepository = electionRepository;
        this.electionService = electionService;
    }

    /**
     * Main sweep — runs every 60 seconds.
     *
     * <p>ShedLock settings:
     * <ul>
     *   <li>{@code lockAtMostFor = "55s"} — release lock even if the node crashes mid-run.</li>
     *   <li>{@code lockAtLeastFor = "10s"} — prevents back-to-back rapid re-runs on restart.</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "electionAutoClose", lockAtMostFor = "55s", lockAtLeastFor = "10s")
    public void sweepAndCloseExpiredElections() {
        Timestamp now = Timestamp.from(Instant.now());

        List<Election> toClose = electionRepository.findElectionsReadyToClose(now);

        if (toClose.isEmpty()) {
            log.debug("ElectionAutoCloseScheduler: no elections to close at {}", now);
            return;
        }

        log.info("ElectionAutoCloseScheduler: found {} election(s) to close", toClose.size());

        int closed = 0;
        int failed  = 0;

        for (Election election : toClose) {
            String reason = resolveReason(election, now);
            try {
                electionService.endElectionSystemAuto(election.getElectionId(), reason);
                closed++;
            } catch (Exception ex) {
                // Log but don't abort the sweep — other elections should still be processed
                log.error("ElectionAutoCloseScheduler: failed to close election [id={}, title='{}'] — {}",
                        election.getElectionId(), election.getElectionTitle(), ex.getMessage(), ex);
                failed++;
            }
        }

        log.info("ElectionAutoCloseScheduler: sweep complete — closed={}, failed={}", closed, failed);
    }

    /**
     * Determines the human-readable close reason for audit purposes.
     * If both conditions apply, TIME_EXPIRED takes priority in the label.
     */
    private String resolveReason(Election election, Timestamp now) {
        boolean timeExpired = election.getElectionEndTime() != null
                && election.getElectionEndTime().before(now);

        boolean voteCountReached = election.isShouldEndAutomatically()
                && election.getExpectedVoterCount() != null
                && election.getTotalVotes() != null
                && election.getTotalVotes() >= election.getExpectedVoterCount();

        if (timeExpired && voteCountReached) {
            return "TIME_EXPIRED_AND_VOTE_COUNT_REACHED";
        } else if (timeExpired) {
            return "TIME_EXPIRED";
        } else {
            return "VOTE_COUNT_REACHED";
        }
    }
}
