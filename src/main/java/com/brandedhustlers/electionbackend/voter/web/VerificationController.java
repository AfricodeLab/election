package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmResponse;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendResponse;
import com.brandedhustlers.electionbackend.voter.service.VerificationService;
import jakarta.mail.MessagingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {
    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<OtpSendResponse> send(@RequestBody OtpSendRequest req) throws MessagingException {
        return ResponseEntity.accepted().body(service.sendOtp(req));
    }

    @PostMapping("/confirm")
    public ResponseEntity<OtpConfirmResponse> confirm(@RequestBody OtpConfirmRequest req) {
        return ResponseEntity.ok(service.confirmOtp(req));
    }

}