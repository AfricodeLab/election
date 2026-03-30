package com.brandedhustlers.electionbackend.results.service;

import com.brandedhustlers.electionbackend.realtime.gateway.RealtimeGateway;
import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TallyProjector {
    private final TallyRepository repo;
    private final ObjectMapper om;
    private final RealtimeGateway realtimeGateway;

    public TallyProjector(TallyRepository repo,
                          ObjectMapper om,
                          RealtimeGateway realtimeGateway) {
        this.repo = repo;
        this.om = om;
        this.realtimeGateway = realtimeGateway;
    }

    @Transactional
    @RabbitListener(queues = "voting.vote.cast")
    public void onVoteCast(String payload) throws Exception {

        JsonNode node = om.readTree(payload);
        UUID electionId = UUID.fromString(node.get("electionId").asText());
        UUID candidateId = UUID.fromString(node.get("candidateId").asText());

        log.info("electionID in listener: {}", electionId);
        log.info("candidateID in listener: {}", candidateId);

        TallyId id = new TallyId(electionId, candidateId);
        Tally t = repo.findById(id)
                .orElseGet(() -> new Tally(id, 0, Timestamp.from(Instant.now())));

        t.setCount(t.getCount() + 1);
        t.setUpdatedAt(Timestamp.from(Instant.now()));

        repo.save(t);

        // Push live tally update to all subscribers watching this election
        realtimeGateway.sendResultsUpdate(
                electionId.toString(),
                Map.of(
                        "electionId",  electionId.toString(),
                        "candidateId", candidateId.toString(),
                        "count",       t.getCount(),
                        "updatedAt",   t.getUpdatedAt().toInstant().toString()
                ));
        log.debug("Pushed tally update for election {} candidate {} count={}",
                electionId, candidateId, t.getCount());
    }
}

//todo how can I let the voter vote again if the vote transaction rolls back