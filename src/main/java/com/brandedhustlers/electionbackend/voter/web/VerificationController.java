package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendRequest;
import com.brandedhustlers.electionbackend.voter.service.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verify/sms")
public class VerificationController {
    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<Void> send(@RequestHeader("X-Org-Id") UUID orgId,
                                     @RequestBody OtpSendRequest req) {
        service.sendOtp(orgId, req.electionId(), req.phoneOrVoterId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestHeader("X-Org-Id") UUID orgId,
                                                       @RequestBody OtpConfirmRequest req) {
        boolean ok = service.confirmOtp(orgId, req.electionId(), req.phoneOrVoterId(), req.token());
        return ResponseEntity.ok(Map.of("verified", ok));
    }
}