package com.brandedhustlers.electionbackend.results.service;

import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.domain.TallyId;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class TallyProjector {
    private final TallyRepository repo;
    private final ObjectMapper om;

    public TallyProjector(TallyRepository repo,
                          ObjectMapper om) {
        this.repo = repo;
        this.om = om;
    }

    @Transactional
    @RabbitListener(queues = "voting.vote.cast")
    public void onVoteCast(String payload) throws Exception {
        JsonNode node = om.readTree(payload);
        UUID electionId = UUID.fromString(node.get("electionId").asText());
        UUID candidateId = UUID.fromString(node.get("candidateId").asText());
        TallyId id = new TallyId(electionId, candidateId);
        Tally t = repo.findById(id).orElseGet(() -> new Tally(id, 0, Timestamp.from(Instant.now())));
        t.setCount(t.getCount() + 1);
        t.setUpdatedAt(Timestamp.from(Instant.now()));
        repo.save(t);
        // TODO push websocket update via RealtimeGateway
    }
}
