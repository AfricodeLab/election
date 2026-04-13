package com.brandedhustlers.electionbackend.common.config.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configures the RabbitMQ connection factory at startup.
 *
 * <p>Priority order:
 * <ol>
 *   <li>{@code CLOUDAMQP_URL} — full AMQP URI, provided by the Heroku CloudAMQP add-on
 *       (e.g. {@code amqps://user:pass@hostname/vhost}).
 *   <li>Individual {@code RABBITMQ_HOST / PORT / USERNAME / PASSWORD} env vars.
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

    // ── Connection factory ────────────────────────────────────────────────────

    @Bean
    @Primary
    public CachingConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();

        String cloudAmqpUrl = System.getenv("CLOUDAMQP_URL");

        if (cloudAmqpUrl != null && !cloudAmqpUrl.isBlank()) {
            // ── Heroku CloudAMQP add-on path ──────────────────────────────────
            log.info("RabbitMQ: configuring from CLOUDAMQP_URL");
            try {
                URI uri = new URI(cloudAmqpUrl);

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

                // Enable TLS for amqps:// (CloudAMQP free tier uses plain amqp,
                // paid tiers use amqps)
                if ("amqps".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        factory.getRabbitConnectionFactory().useSslProtocol();
                    } catch (Exception e) {
                        log.warn("RabbitMQ: could not enable SSL — {}", e.getMessage());
                    }
                }

            } catch (URISyntaxException e) {
                log.error("RabbitMQ: CLOUDAMQP_URL is malformed — falling back to defaults. {}", e.getMessage());
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

    private void applyIndividualVars(CachingConnectionFactory factory) {
        String host     = envOrDefault("RABBITMQ_HOST",     "localhost");
        String port     = envOrDefault("RABBITMQ_PORT",     "5672");
        String username = envOrDefault("RABBITMQ_USERNAME", "guest");
        String password = envOrDefault("RABBITMQ_PASSWORD", "guest");
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
