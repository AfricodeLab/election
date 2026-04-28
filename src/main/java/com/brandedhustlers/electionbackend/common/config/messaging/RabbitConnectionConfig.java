package com.brandedhustlers.electionbackend.common.config.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.backoff.ExponentialBackOff;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configures the RabbitMQ connection factory at startup.
 *
 * <p>Priority order:
 * <ol>
 *   <li>{@code CLOUDAMQP_URL} — full AMQP URI set by the Heroku CloudAMQP add-on
 *       (e.g. {@code amqps://user:pass@hostname/vhost}).
 *       <strong>Must be an AMQP URI — do not store a Redis URL here.</strong>
 *   <li>{@code RABBITMQ_URL} — full AMQP URI set manually
 *       (e.g. {@code amqps://user:pass@hostname/vhost}).
 *   <li>Individual {@code RABBITMQ_HOST / PORT / USERNAME / PASSWORD / VHOST} env vars.
 *   <li>Localhost defaults ({@code guest/guest}) — starts the app cleanly but
 *       messaging will be a no-op until a real broker is wired up.
 * </ol>
 *
 * <p>Auth failures and missing-broker situations are made non-fatal:
 * <ul>
 *   <li>Set {@code RABBITMQ_LISTENERS_ENABLED=false} on Heroku to prevent listener
 *       containers from starting at all (useful while you provision a broker).
 *   <li>Once a broker is provisioned just set the correct env vars and redeploy —
 *       listeners will start automatically.
 * </ul>
 */
@Configuration
public class RabbitConnectionConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConnectionConfig.class);

    @Value("${app.rabbitmq.host}")
    private String hostName ;
    @Value("${app.rabbitmq.port}")
    private String portName;
    @Value("${app.rabbitmq.username}")
    private String userName;
    @Value("${app.rabbitmq.password}")
    private String passWord;

    // ── Connection factory ────────────────────────────────────────────────────

    @Bean
    @Primary
    public CachingConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();

        // Resolve the AMQP URI: CLOUDAMQP_URL → RABBITMQ_URL → individual vars → localhost
        String amqpUrl = resolveAmqpUrl();

        if (amqpUrl != null) {
            log.info("RabbitMQ: configuring from URL (scheme={})", amqpUrl.substring(0, amqpUrl.indexOf("://")));
            try {
                URI uri = new URI(amqpUrl);

                // Guard: reject non-AMQP URLs (e.g. someone accidentally set CLOUDAMQP_URL
                // to a Redis URL — that was the bug that caused the redis-host-dialing issue).
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.startsWith("amqp"))) {
                    log.error("RabbitMQ: URL has non-AMQP scheme '{}' — ignoring and falling back to individual vars. " +
                              "Check that CLOUDAMQP_URL / RABBITMQ_URL contain an amqp:// or amqps:// URI.", scheme);
                    applyIndividualVars(factory);
                } else {
                    factory.setHost(uri.getHost());
                    factory.setPort(uri.getPort() > 0 ? uri.getPort() : 5672);

                    String userInfo = uri.getUserInfo(); // "user:password"
                    if (userInfo != null) {
                        String[] parts = userInfo.split(":", 2);
                        factory.setUsername(parts[0]);
                        if (parts.length > 1) factory.setPassword(parts[1]);
                    }

                    // Path component is the vhost, e.g. "/myvhost" → "myvhost"
                    String path = uri.getPath();
                    if (path != null && path.length() > 1) {
                        factory.setVirtualHost(path.substring(1));
                    }

                    if ("amqps".equalsIgnoreCase(scheme)) {
                        try {
                            factory.getRabbitConnectionFactory().useSslProtocol();
                        } catch (Exception e) {
                            log.warn("RabbitMQ: could not enable SSL — {}", e.getMessage());
                        }
                    }
                }

            } catch (URISyntaxException e) {
                log.error("RabbitMQ: URL is malformed — falling back to individual vars. {}", e.getMessage());
                applyIndividualVars(factory);
            }

        } else {
            // ── Individual env-var path (or localhost default) ────────────────
            applyIndividualVars(factory);
        }

        // Short connection timeout so a missing broker fails fast instead of blocking startup
        factory.setConnectionTimeout(6_000);

        return factory;
    }

    /** Returns the first non-blank AMQP URL found, or null if none are set. */
    private String resolveAmqpUrl() {
        String v = System.getenv("CLOUDAMQP_URL");
        if (v != null && !v.isBlank()) { log.info("RabbitMQ: using CLOUDAMQP_URL"); return v; }
        v = System.getenv("RABBITMQ_URL");
        if (v != null && !v.isBlank()) { log.info("RabbitMQ: using RABBITMQ_URL"); return v; }
        return null;
    }

    private void applyIndividualVars(CachingConnectionFactory factory) {
        String host     = envOrDefault("RABBITMQ_HOST",     hostName);
        String port     = envOrDefault("RABBITMQ_PORT",     portName);
        String username = envOrDefault("RABBITMQ_USERNAME", userName);
        String password = envOrDefault("RABBITMQ_PASSWORD", passWord);
        String vhost    = envOrDefault("RABBITMQ_VHOST",    "/");

        log.info("RabbitMQ: connecting to {}:{} (vhost={})", host, port, vhost);

        factory.setHost(host);
        factory.setPort(Integer.parseInt(port));
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(vhost);
    }

    private String envOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    // ── Listener container factory ────────────────────────────────────────────

    /**
     * Overrides Spring Boot's default {@code SimpleRabbitListenerContainerFactory}.
     *
     * <p>Key changes vs the default:
     * <ul>
     *   <li>{@code missingQueuesFatal=false} — broker is unavailable at startup → log, don't crash.
     *   <li>{@code autoStartup} tied to {@code RABBITMQ_LISTENERS_ENABLED} env var
     *       (default {@code true}).  Set the var to {@code false} on Heroku to skip listener
     *       startup while you're still provisioning a broker.
     * </ul>
     */
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setMissingQueuesFatal(false);
        factory.setDefaultRequeueRejected(false);

        // Cap consumer threads: 1 concurrent consumer per listener, max 1.
        // Prevents the "failed → restart → new thread → failed" loop from
        // blowing up memory when the broker is unreachable.
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);

        // Exponential back-off on consumer restart: 5s → 10s → 20s … max 60s.
        // Without this, a broken connection spawns a new consumer thread every
        // few milliseconds, exhausting heap and causing R14 on Heroku.
        // Uses spring-core's ExponentialBackOff (not spring-retry's ExponentialBackOffPolicy).
        ExponentialBackOff backOff =
                new ExponentialBackOff(5_000L, 2.0);
        backOff.setMaxInterval(60_000L);
        factory.setRecoveryBackOff(backOff);

        boolean autoStart = !"false".equalsIgnoreCase(
                System.getenv().getOrDefault("RABBITMQ_LISTENERS_ENABLED", "true"));

        factory.setAutoStartup(autoStart);

        if (!autoStart) {
            log.warn("RabbitMQ listeners are DISABLED (RABBITMQ_LISTENERS_ENABLED=false). " +
                     "Set it to 'true' and redeploy once a broker is provisioned.");
        }

        return factory;
    }
}
