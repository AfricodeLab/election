package com.brandedhustlers.electionbackend.common.config.websocket;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    @Value("${spring.rabbitmq.host}")
    private String rabbitHost;

    @Value("${rabbitmq.stomp.port:61613}")
    private int rabbitStompPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitUser;

    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    public WebsocketConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.allowedOrigins = dotenv.get("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(",");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
        registry.addEndpoint("/ws-raw").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(rabbitHost)
                .setRelayPort(rabbitStompPort)
                .setClientLogin(rabbitUser)
                .setClientPasscode(rabbitPassword)
                .setSystemLogin(rabbitUser)
                .setSystemPasscode(rabbitPassword);
        registry.setApplicationDestinationPrefixes("/app");
    }
}
