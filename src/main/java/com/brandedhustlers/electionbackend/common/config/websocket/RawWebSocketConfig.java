package com.brandedhustlers.electionbackend.common.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final String[] allowedOrigins;

    public RawWebSocketConfig() {
        this.allowedOrigins = new String[]{System.getenv("CORS_ALLOWED_ORIGINS")};
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MyWebSocketHandler(), "/ws/raw").setAllowedOrigins(allowedOrigins);
    }
}