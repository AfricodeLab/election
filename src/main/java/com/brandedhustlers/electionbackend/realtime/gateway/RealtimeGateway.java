package com.brandedhustlers.electionbackend.realtime.gateway;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimeGateway {
    private final SimpMessagingTemplate ws;

    public RealtimeGateway(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void sendResultsUpdate(String electionId, Object payload) {
        ws.convertAndSend("/topic/election/results/" + electionId, payload);
    }

    public void sendDeviceStatus(String stationId, Object payload) {
        ws.convertAndSend("/topic/station/" + stationId, payload);
    }

    public void sendVoteAck(String destinationKey, Object payload) {
        ws.convertAndSend("/queue/vote-ack/" + destinationKey, payload);
    }
}