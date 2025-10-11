package com.brandedhustlers.electionbackend.common.config.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "app.exchange";
    public static final String AUTH_QUEUE = "auth.events";
    public static final String ELECTION_QUEUE = "election.events";
    public static final String ROUTING_KEY_AUTH = "auth.#";
    public static final String ROUTING_KEY_ELECTION = "election.#";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue authQueue() {
        return QueueBuilder.durable(AUTH_QUEUE).build();
    }

    @Bean
    public Queue electionQueue() {
        return QueueBuilder.durable(ELECTION_QUEUE).build();
    }

    @Bean
    public Binding authBinding() {
        return BindingBuilder.bind(authQueue())
                .to(exchange())
                .with(ROUTING_KEY_AUTH);
    }

    @Bean
    public Binding electionBinding() {
        return BindingBuilder.bind(electionQueue())
                .to(exchange())
                .with(ROUTING_KEY_ELECTION);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}

