package com.brandedhustlers.electionbackend.common.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final MyWebSocketHandler myWebSocketHandler;

    /**
     * Inject the Spring-managed bean instead of using {@code new MyWebSocketHandler()}.
     * The previous approach bypassed the Spring proxy — @Autowired fields inside
     * MyWebSocketHandler were never populated and @Component had no effect.
     */
    public RawWebSocketConfig(MyWebSocketHandler myWebSocketHandler) {
        this.myWebSocketHandler = myWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String rawOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        String[] allowedOrigins = (rawOrigins != null && !rawOrigins.isBlank())
                ? rawOrigins.split(",") : new String[]{"*"};
        registry.addHandler(myWebSocketHandler, "/ws/raw").setAllowedOrigins(allowedOrigins);
    }
}