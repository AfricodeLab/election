package com.brandedhustlers.electionbackend.common.config.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket / STOMP broker configuration.
 *
 * <p>By default uses Spring's in-memory simple broker so the app starts cleanly
 * without any external RabbitMQ connection.  The STOMP relay can be enabled via
 * the {@code STOMP_RELAY_ENABLED=true} environment variable once a broker with
 * the STOMP plugin is provisioned (CloudAMQP Tiger plan or higher).
 *
 * <p>Required env vars when STOMP_RELAY_ENABLED=true:
 * <ul>
 *   <li>{@code RABBITMQ_HOST} — broker hostname
 *   <li>{@code RABBITMQ_USERNAME} — STOMP login
 *   <li>{@code RABBITMQ_PASSWORD} — STOMP passcode
 *   <li>{@code RABBITMQ_STOMP_PORT} — STOMP port (default 61613)
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfig.class);

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = resolveAllowedOrigins();
        registry.addEndpoint("/ws").setAllowedOrigins(origins).withSockJS();
        registry.addEndpoint("/ws-raw").setAllowedOrigins(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");

        boolean stompRelayEnabled = "true".equalsIgnoreCase(
                System.getenv().getOrDefault("STOMP_RELAY_ENABLED", "false"));

        if (stompRelayEnabled) {
            String host     = env("RABBITMQ_HOST",     "localhost");
            String user     = env("RABBITMQ_USERNAME", "guest");
            String password = env("RABBITMQ_PASSWORD", "guest");
            int    port     = Integer.parseInt(env("RABBITMQ_STOMP_PORT", "61613"));

            log.info("WebSocket: STOMP relay → {}:{}", host, port);
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(host)
                    .setRelayPort(port)
                    .setClientLogin(user)
                    .setClientPasscode(password)
                    .setSystemLogin(user)
                    .setSystemPasscode(password);
        } else {
            log.info("WebSocket: using in-memory simple broker " +
                     "(set STOMP_RELAY_ENABLED=true to use external RabbitMQ STOMP)");
            registry.enableSimpleBroker("/topic", "/queue");
        }
    }

    /**
     * Guards against slow/stale WebSocket subscribers accumulating unbounded
     * pending-message buffers in heap.
     * - 8 KB max inbound message (sufficient for all current client payloads)
     * - 256 KB per-session send buffer (Spring default is 512 KB)
     * - 15 s send timeout — disconnect a subscriber that hasn't drained after 15 s
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8 * 1024);
        registration.setSendBufferSizeLimit(256 * 1024);
        registration.setSendTimeLimit(15 * 1000);
    }

    private String[] resolveAllowedOrigins() {
        String raw = System.getenv("CORS_ALLOWED_ORIGINS");
        if (raw != null && !raw.isBlank()) {
            return raw.split(",");
        }
        return new String[]{"*"};
    }

    private String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
