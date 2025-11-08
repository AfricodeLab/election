package com.brandedhustlers.electionbackend.voting.service;

import com.brandedhustlers.electionbackend.common.config.messaging.RabbitMQConfig;
import com.brandedhustlers.electionbackend.voting.repository.OutBoxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class OutBoxService implements OutBoxRelay {
    private final OutBoxRepository outbox;
    private final RabbitTemplate rabbit;

    public OutBoxService(OutBoxRepository outbox, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    @Override
    @Transactional
//    @Scheduled(fixedDelay = 10000)//every 10 seconds
    public void publishPending() {
        outbox.findAll().stream().filter(o -> o.getPublishedAt() == null).limit(100).forEach(o -> {
            rabbit.convertAndSend(RabbitMQConfig.VOTING_VOTE_CAST, o.getPayload());
            o.setPublishedAt(Timestamp.from(Instant.now()));
            outbox.save(o);
        });
    }
}
