package com.brandedhustlers.electionbackend.common.config.websocket;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final String[] allowedOrigins;

    public RawWebSocketConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.allowedOrigins = dotenv.get("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MyWebSocketHandler(), "/ws/raw").setAllowedOrigins(allowedOrigins);
    }
}