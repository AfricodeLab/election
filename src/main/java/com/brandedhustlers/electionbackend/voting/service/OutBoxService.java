package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.common.config.messaging.RabbitMQConfig;
import com.brandedhustlers.electionbackend.voting.domain.OutBox;
import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.brandedhustlers.electionbackend.voting.enums.PublishStatus;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import com.brandedhustlers.electionbackend.voting.repository.VoteRepository;
import com.brandedhustlers.electionbackend.voting.util.Hash;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class OutBoxService implements OutBoxRelay {
    private final OutBoxRepository outbox;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final Hash getHash;
    private final VoteRepository voteRepository;

    public OutBoxService(OutBoxRepository outbox,
                         RabbitTemplate rabbit,
                         ObjectMapper objectMapper, Hash getHash, VoteRepository voteRepository) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.getHash = getHash;
        this.voteRepository = voteRepository;
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 10000)//every 10 seconds
    public void publishPending() {
        List<OutBox> batch = outbox.findTopByPublishedAtIsNull();
        for (OutBox o : batch) {
            try {
                // Deserialize payload back to Vote
                Vote vote = objectMapper.readValue(o.getPayload(), Vote.class);

                Vote tableVote = voteRepository.findById(vote.getId())
                        .orElseThrow(() -> new IllegalArgumentException("vote not found"));

                // Recompute hash from the current state
                String outboxVoteHash = getHash.hashVote(vote);
                String currentVoteHash = getHash.hashVote(tableVote);

                if (!outboxVoteHash.equals(currentVoteHash)) {
                    log.warn("Vote {} has changed since outbox entry created, skipping", vote.getId());
                    o.setPublishStatus(PublishStatus.FAILED_VALIDATION);
                    o.setFailedValidationAt(Timestamp.from(Instant.now()));
                    continue;
                }

                rabbit.convertAndSend(RabbitMQConfig.VOTING_VOTE_CAST, o.getPayload());
                o.setPublishedAt(Timestamp.from(Instant.now()));
                outbox.save(o);
            } catch (Exception e) {
                log.error("Failed to publish outbox entry {}", o.getId(), e);
            }
        }
    }
}
