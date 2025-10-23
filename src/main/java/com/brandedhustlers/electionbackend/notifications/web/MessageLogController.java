package com.brandedhustlers.electionbackend.notifications.web;

import com.brandedhustlers.electionbackend.notifications.domain.MessageLog;
import com.brandedhustlers.electionbackend.notifications.repository.MessageLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notif/messages")
public class MessageLogController {
    private final MessageLogRepository repo;

    public MessageLogController(MessageLogRepository repo) {
        this.repo = repo;
    }


    @GetMapping("/{messageId}")
    public ResponseEntity<MessageLog> get(@PathVariable UUID messageId) {
        return repo.findById(messageId).map(row -> {
            // Mask recipient for safety on read
            row.setToAddress(mask(row.getToAddress()));
            return ResponseEntity.ok(row);
        }).orElse(ResponseEntity.notFound().build());
    }

    private String mask(String v) {
        if (v == null) {
            return null;
        }
        int at = v.indexOf('@');
        if (at > 1) {
            return v.charAt(0) + "***" + v.substring(at - 1);
        }
        return "***";
    }
}