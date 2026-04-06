package com.brandedhustlers.electionbackend.common.config.websocket;

import com.brandedhustlers.electionbackend.realtime.gateway.RealtimeGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/test/ws")
public class WebSocketTestController {

    private final RealtimeGateway gateway;

    public WebSocketTestController(RealtimeGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/results/{electionId}")
    @MessageMapping("/results")
    @SendTo("/topic/results")
    public ResponseEntity<?> pushResults(
            @PathVariable String electionId,
            @RequestParam UUID orgId,
            @RequestBody Map<String, String> body) {
        gateway.sendResultsUpdate(orgId, electionId, body);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/devices/{stationId}")
    public ResponseEntity<?> pushDevice(
            @PathVariable String stationId,
            @RequestParam UUID orgId,
            @RequestBody Map<String, Object> body) {
        gateway.sendDeviceStatus(orgId, stationId, body);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/votes/ack/{key}")
    public ResponseEntity<?> pushAck(
            @PathVariable String key,
            @RequestParam UUID orgId,
            @RequestBody Map<String, Object> body) {
        gateway.sendVoteAck(orgId, key, body);
        return ResponseEntity.ok(Map.of("sent", true));
    }
}
