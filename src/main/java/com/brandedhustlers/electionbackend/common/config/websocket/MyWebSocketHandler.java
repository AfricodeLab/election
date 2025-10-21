package com.brandedhustlers.electionbackend.common.config.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class MyWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, IOException {
        String payload = message.getPayload();
        System.out.println("Received: " + payload);

        session.sendMessage(new TextMessage("Echo: " + payload));
    }

}