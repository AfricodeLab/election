package com.brandedhustlers.electionbackend.realtime.gateway;

import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.RequiresFeature;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimeGateway {
    private final SimpMessagingTemplate ws;

    public RealtimeGateway(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    @RequiresFeature(Feature.REAL_TIME_RESULTS)
    public void sendResultsUpdate(String electionId, Object payload) {
        ws.convertAndSend("/topic/election/results/" + electionId, payload);
    }

    @RequiresFeature(Feature.REAL_TIME_RESULTS)
    public void sendDeviceStatus(String stationId, Object payload) {
        ws.convertAndSend("/topic/station/" + stationId, payload);
    }

    @RequiresFeature(Feature.REAL_TIME_RESULTS)
    public void sendVoteAck(String destinationKey, Object payload) {
        ws.convertAndSend("/queue/vote-ack/" + destinationKey, payload);
    }
}
