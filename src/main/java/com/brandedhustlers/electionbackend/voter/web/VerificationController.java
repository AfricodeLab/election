package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmResponse;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendResponse;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voter.service.AttributeExtractor;
import com.brandedhustlers.electionbackend.voter.service.VerificationService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verification")
@Slf4j
public class VerificationController {
    private final VerificationService service;
    private final VoterRepository voterRepository;
    private final ElectionRepository electionRepository;
    private final GetUser getUser;
    private final AttributeExtractor attributeExtractor;

    public VerificationController(VerificationService service,
                                  VoterRepository voterRepository,
                                  ElectionRepository electionRepository,
                                  GetUser getUser,
                                  AttributeExtractor attributeExtractor) {
        this.service = service;
        this.voterRepository = voterRepository;
        this.electionRepository = electionRepository;
        this.getUser = getUser;
        this.attributeExtractor = attributeExtractor;
    }

    @PostMapping("/send")
    public ResponseEntity<OtpSendResponse> send(@RequestBody OtpSendRequest req) throws MessagingException {
        return ResponseEntity.accepted().body(service.sendOtp(req));
    }

    @PostMapping("/confirm")
    public ResponseEntity<OtpConfirmResponse> confirm(@RequestBody OtpConfirmRequest req, HttpServletResponse response) {
        return ResponseEntity.ok(service.confirmOtp(req, response));
    }

    @GetMapping("/voters/attribute-coverage")
    public ResponseEntity<?> getAttributeCoverage(@RequestParam UUID electionId) {
        try {
            AppUser admin = getUser.getCurrentAuthenticatedUser();
            if (!admin.getUserRole().equals(UserRole.ADMIN)) {
                return ResponseEntity.status(403).body("Admin only");
            }

            Election election = electionRepository.findById(electionId).orElseThrow();
            EligibilityPolicy policy = election.getEligibilityPolicy();

            // Get all voters in org
            List<Voter> voters = voterRepository.findByOrgId((admin.getAppUserOrganization().getOrganizationId()));

            int total = voters.size();
            int complete = 0;
            List<Map<String, Object>> incomplete = new ArrayList<>();

            for (Voter voter : voters) {
                List<String> missing = attributeExtractor.validateRequiredAttributes(
                        voter.getAttributes(),
                        policy
                );

                if (missing.isEmpty()) {
                    complete++;
                } else {
                    incomplete.add(Map.of(
                            "voterId", voter.getVoterId(),
                            "name", voter.getName() != null ? voter.getName() : "",
                            "email", voter.getEmail() != null ? voter.getEmail() : "",
                            "missingAttributes", missing
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "total", total,
                    "complete", complete,
                    "incomplete", total - complete,
                    "coverage", complete * 100.0 / total,
                    "incompleteVoters", incomplete.stream().limit(50).toList()  // First 50
            ));

        } catch (Exception e) {
            log.error("Failed to get attribute coverage", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}