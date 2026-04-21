package com.brandedhustlers.electionbackend.voter.web;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import com.brandedhustlers.electionbackend.common.util.GetUser;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.domain.EligibilityPolicy;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.*;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import com.brandedhustlers.electionbackend.voter.service.AttributeExtractor;
import com.brandedhustlers.electionbackend.voter.service.VerificationService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

    // ── Public (no auth required — voters don't have admin accounts) ──────────

    /**
     * Step 1 of remote verification: validate email + national ID, dispatch OTP.
     */
    @PostMapping("/send")
    public ResponseEntity<OtpSendResponse> send(
            @Valid @RequestBody OtpSendRequest req) throws MessagingException {
        return ResponseEntity.accepted().body(service.sendOtp(req));
    }

    /**
     * Step 2 of remote verification (also used for station-PIN confirmation in
     * the in-person flow): validate code, issue voterToken.
     */
    @PostMapping("/confirm")
    public ResponseEntity<OtpConfirmResponse> confirm(
            @Valid @RequestBody OtpConfirmRequest req,
            HttpServletResponse response) {
        return ResponseEntity.ok(service.confirmOtp(req, response));
    }

    // ── Admin-only (requires auth) ────────────────────────────────────────────

    /**
     * In-person verification: an official confirms the voter's physical ID and
     * calls this endpoint. The system marks the voter as identity-verified and
     * returns a short-lived station PIN that the voter uses at the terminal.
     *
     * <p>Secured at the HTTP layer by {@code SecurityConfig} (ADMIN roles only).
     * The service layer additionally records which official performed the action.</p>
     */
    @PostMapping("/in-person/{voterId}/{electionId}")
    public ResponseEntity<InPersonVerifyResponse> verifyInPerson(
            @PathVariable UUID voterId,
            @PathVariable UUID electionId) {

        AppUser official = getUser.getCurrentAuthenticatedUser();
        InPersonVerifyResponse result =
                service.verifyInPerson(voterId, electionId, official);
        return ResponseEntity.ok(result);
    }

    // ── Admin read (attribute coverage) ──────────────────────────────────────

    @GetMapping("/voters/attribute-coverage")
    public ResponseEntity<?> getAttributeCoverage(@RequestParam UUID electionId) {
        try {
            AppUser admin = getUser.getCurrentAuthenticatedUser();
            if (!admin.getUserRole().equals(UserRole.ADMIN)) {
                return ResponseEntity.status(403).body("Admin only");
            }

            Election election = electionRepository.findById(electionId).orElseThrow();
            EligibilityPolicy policy = election.getEligibilityPolicy();

            Organization adminOrg = admin.getActiveOrganization();
            if (adminOrg == null) {
                return ResponseEntity.status(403).body("No active organization");
            }

            List<Voter> orgVoters = voterRepository.findByOrgId(adminOrg.getOrganizationId());

            int total    = orgVoters.size();
            int complete = 0;
            List<Map<String, Object>> incomplete = new ArrayList<>();

            for (Voter voter : orgVoters) {
                List<String> missing = attributeExtractor.validateRequiredAttributes(
                        voter.getAttributes(), policy);

                if (missing.isEmpty()) {
                    complete++;
                } else {
                    incomplete.add(Map.of(
                            "voterId", voter.getVoterId(),
                            "name",    voter.getName() != null ? voter.getName() : "",
                            "email",   voter.getEmail() != null ? voter.getEmail() : "",
                            "missingAttributes", missing));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "total",            total,
                    "complete",         complete,
                    "incomplete",       total - complete,
                    "coverage",         total > 0 ? complete * 100.0 / total : 0.0,
                    "incompleteVoters", incomplete.stream().limit(50).toList()));

        } catch (Exception e) {
            log.error("Failed to get attribute coverage", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
