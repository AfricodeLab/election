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
    private final Hash getHash;  // Salt is already in Hash class
    private final VoteRepository voteRepository;

    public OutBoxService(OutBoxRepository outbox,
                         RabbitTemplate rabbit,
                         ObjectMapper objectMapper,
                         Hash getHash,
                         VoteRepository voteRepository) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.getHash = getHash;
        this.voteRepository = voteRepository;
    }

    @Override
    @Transactional
//    @Scheduled(fixedDelay = 10000)//every 10 seconds
    public void publishPending() {
        List<OutBox> batch = outbox.findTopByPublishedAtIsNull();
        for (OutBox o : batch) {
            try {
                log.info("Processing outbox entry {} for vote {}", o.getId(), o.getAggregateId());

                Vote tableVote = voteRepository.findById(o.getAggregateId())
                        .orElseThrow(() -> new IllegalArgumentException("vote not found: " + o.getAggregateId()));

                String currentVoteHash = getHash.hashVote(tableVote);

                if (!o.getVoteHash().equals(currentVoteHash)) {
                    log.warn("Vote {} has changed since outbox entry created. Expected hash: {}, Current hash: {}",
                            o.getAggregateId(), o.getVoteHash(), currentVoteHash);
                    o.setPublishStatus(PublishStatus.FAILED_VALIDATION);
                    o.setFailedValidationAt(Timestamp.from(Instant.now()));
                    outbox.save(o);
                    continue;
                }

                rabbit.convertAndSend(RabbitMQConfig.VOTING_VOTE_CAST, o.getPayload());
                o.setPublishedAt(Timestamp.from(Instant.now()));
                o.setFailedValidationAt(null);
                o.setPublishStatus(PublishStatus.SUCCESS);
                outbox.save(o);

                log.info("Successfully published vote {} from outbox entry {}",
                        o.getAggregateId(), o.getId());

            } catch (Exception e) {
                log.error("Failed to publish outbox entry {} for vote {}",
                        o.getId(), o.getAggregateId(), e);
            }
        }
    }
}