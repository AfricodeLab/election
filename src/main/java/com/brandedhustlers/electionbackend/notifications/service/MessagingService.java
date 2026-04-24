package com.brandedhustlers.electionbackend.notifications.service;

import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.election.repository.ElectionRepository;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.repository.LaunchElectionRepository;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.voter.domain.EligibleVoterElection;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.EligibleVoterElectionRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessagingService {
    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final JavaMailSender mailSender;
    private final VoterRepository voterRepository;
    private final TemplateEngine templateEngine;
    private final LaunchRepository launchRepository;
    private final LaunchElectionRepository launchElectionRepository;
    private final ElectionRepository electionRepository;
    private final EligibleVoterElectionRepository eligibleVoterElectionRepository;

    private final String appEmail = System.getenv("SPRING_MAIL_USERNAME");

    /**
     * Frontend base URL — used to construct the per-election voting deep-link
     * sent in election-live emails.  Defaults to http://localhost:3000 for dev;
     * override via APP_BASE_URL env var in production.
     */
    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    public MessagingService(JavaMailSender mailSender,
                            VoterRepository voterRepository,
                            TemplateEngine templateEngine,
                            LaunchRepository launchRepository,
                            LaunchElectionRepository launchElectionRepository,
                            ElectionRepository electionRepository,
                            EligibleVoterElectionRepository eligibleVoterElectionRepository) {
        this.mailSender = mailSender;
        this.voterRepository = voterRepository;
        this.templateEngine = templateEngine;
        this.launchRepository = launchRepository;
        this.launchElectionRepository = launchElectionRepository;
        this.electionRepository = electionRepository;
        this.eligibleVoterElectionRepository = eligibleVoterElectionRepository;
    }

    /**
     * Central voter notification listener on the notif.email queue.
     *
     * Two payload shapes are accepted:
     *
     *   Launch path (multiple elections at once):
     *     { "launchId": "<uuid>", "orgId": "<uuid>" }
     *     Sent by LaunchService when a Launch transitions PLANNED → ACTIVE.
     *     Snapshot already computed by EligibilitySnapshotService.computeForLaunch().
     *
     *   Standalone path (single election):
     *     { "electionId": "<uuid>", "orgId": "<uuid>" }
     *     Sent by ElectionService.transitionToLive() when a standalone election
     *     (not part of any Launch) is brought live directly.
     *     Snapshot already computed by EligibilitySnapshotService.computeForElection().
     *
     * The guard in ElectionService.transitionToLive() ensures elections that belong
     * to a Launch never emit a standalone notification, preventing double-emails.
     */
    @RabbitListener(queues = "notif.email")
    public void sendElectionLiveEmail(Map<String, Object> payload) {
        try {
            if (payload == null) {
                log.error("Received null payload on notif.email queue — ignoring");
                return;
            }

            if (payload.containsKey("launchId")) {
                handleLaunchNotification(payload);
            } else if (payload.containsKey("electionId")) {
                handleStandaloneElectionNotification(payload);
            } else {
                log.error("Unknown payload shape on notif.email — missing both 'launchId' and 'electionId': {}", payload.keySet());
            }

        } catch (Exception e) {
            log.error("Unexpected error processing election notification", e);
            throw new RuntimeException("Failed to process election notification", e);
        }
    }

    // ── Launch path ───────────────────────────────────────────────────────────

    private void handleLaunchNotification(Map<String, Object> payload) {
        if (!payload.containsKey("orgId")) {
            log.error("Launch payload missing orgId");
            return;
        }

        UUID launchId = UUID.fromString(payload.get("launchId").toString());
        UUID orgId    = UUID.fromString(payload.get("orgId").toString());

        log.info("Processing launch notification: launchId={}, orgId={}", launchId, orgId);

        // Verify launch exists
        launchRepository.findById(launchId)
                .orElseThrow(() -> new LaunchNotfound("Launch not found: " + launchId));

        // Fetch elections via the join table (avoids touching the LAZY Launch.election collection)
        List<UUID> electionIds = launchElectionRepository.findByIdLaunchId(launchId)
                .stream()
                .map(le -> le.getId().getElectionId())
                .toList();

        List<Election> elections = electionRepository.findAllById(electionIds);
        if (elections.isEmpty()) {
            log.warn("No elections found for launch {} — skipping notification", launchId);
            return;
        }

        Map<UUID, Election> electionMap = elections.stream()
                .collect(Collectors.toMap(Election::getElectionId, Function.identity()));

        // Read pre-computed eligible voter snapshot (keyed by launchId)
        List<UUID> eligibleVoterIds = eligibleVoterElectionRepository
                .findDistinctVoterIdsByLaunchId(launchId);

        if (eligibleVoterIds.isEmpty()) {
            log.warn("No eligible voters found for launch {} — skipping notification", launchId);
            return;
        }

        List<Voter> voters = voterRepository.findAllById(eligibleVoterIds);

        Map<UUID, Set<UUID>> voterEligibleElections = eligibleVoterElectionRepository
                .findByLaunchId(launchId)
                .stream()
                .collect(Collectors.groupingBy(
                        EligibleVoterElection::getVoterId,
                        Collectors.mapping(EligibleVoterElection::getElectionId, Collectors.toSet())));

        log.info("Sending launch emails to {} voters across {} elections", voters.size(), elections.size());
        dispatchEmails(voters, voterEligibleElections, electionMap);
    }

    // ── Standalone election path ──────────────────────────────────────────────

    private void handleStandaloneElectionNotification(Map<String, Object> payload) {
        if (!payload.containsKey("orgId")) {
            log.error("Standalone election payload missing orgId");
            return;
        }

        UUID electionId = UUID.fromString(payload.get("electionId").toString());
        UUID orgId      = UUID.fromString(payload.get("orgId").toString());

        log.info("Processing standalone election notification: electionId={}, orgId={}", electionId, orgId);

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ElectionNotFound("Election not found: " + electionId));

        // Read pre-computed eligible voter snapshot (launchId IS NULL for standalone)
        List<UUID> eligibleVoterIds = eligibleVoterElectionRepository
                .findDistinctVoterIdsByElectionId(electionId);

        if (eligibleVoterIds.isEmpty()) {
            log.warn("No eligible voters found for standalone election {} — skipping notification", electionId);
            return;
        }

        List<Voter> voters = voterRepository.findAllById(eligibleVoterIds);

        // Every eligible voter gets the same single election
        Map<UUID, Set<UUID>> voterEligibleElections = voters.stream()
                .collect(Collectors.toMap(
                        Voter::getVoterId,
                        v -> Set.of(electionId)));

        Map<UUID, Election> electionMap = Map.of(electionId, election);

        log.info("Sending standalone election emails to {} voters", voters.size());
        dispatchEmails(voters, voterEligibleElections, electionMap);
    }

    // ── Shared dispatch logic ─────────────────────────────────────────────────

    private void dispatchEmails(List<Voter> voters,
                                Map<UUID, Set<UUID>> voterEligibleElections,
                                Map<UUID, Election> electionMap) {
        int successCount = 0;
        int failureCount = 0;

        for (Voter voter : voters) {
            Set<UUID> eligibleElectionIds = voterEligibleElections
                    .getOrDefault(voter.getVoterId(), Collections.emptySet());

            List<Election> voterElections = eligibleElectionIds.stream()
                    .map(electionMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            if (voterElections.isEmpty()) continue;

            Timestamp earliestStart = voterElections.stream()
                    .map(Election::getElectionStartTime)
                    .filter(Objects::nonNull)
                    .min(Timestamp::compareTo)
                    .orElse(null);

            Timestamp latestEnd = voterElections.stream()
                    .map(Election::getElectionEndTime)
                    .filter(Objects::nonNull)
                    .max(Timestamp::compareTo)
                    .orElse(null);

            try {
                sendSingleEmail(voter, voterElections, earliestStart, latestEnd);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to send email to voter {}: {}", voter.getEmail(), e.getMessage(), e);
            }
        }

        log.info("Email dispatch complete — success: {}, failures: {}", successCount, failureCount);
    }

    /**
     * Compose and send a single election-live notification email to one voter.
     *
     * @param voter          the recipient
     * @param elections      elections this voter is eligible to vote in
     * @param startTime      earliest election start across the batch
     * @param endTime        latest election end across the batch
     */
    @Async
    public void sendSingleEmail(Voter voter, List<Election> elections,
                                Timestamp startTime, Timestamp endTime)
            throws MessagingException {

        List<String> electionTitles = elections.stream()
                .map(Election::getElectionTitle)
                .toList();

        // Single portal link — voters authenticate once and see all eligible elections.
        String portalUrl = appBaseUrl + "/portal";

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        Context context = new Context();
        context.setVariable("voterName", voter.getName());
        context.setVariable("electionTitles", electionTitles);
        context.setVariable("portalUrl", portalUrl);
        context.setVariable("startTime", startTime);
        context.setVariable("endTime", endTime);

        String htmlContent = templateEngine.process("ElectionLive", context);

        helper.setFrom(appEmail);
        helper.setTo(voter.getEmail());

        String subject = electionTitles.size() == 1
                ? "Election Now Live: " + electionTitles.getFirst()
                : "Elections Now Live: " + electionTitles.size() + " Elections";

        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        log.debug("Sending email to {} for {} election(s)", voter.getEmail(), electionTitles.size());
        mailSender.send(mime);
    }

    @Async
    public void sendVerificationEmail(String recipientEmail, String firstName, String verificationUrl)
            throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        Context ctx = new Context();
        ctx.setVariable("firstName", firstName);
        ctx.setVariable("verificationUrl", verificationUrl);

        String html = templateEngine.process("EmailVerification", ctx);

        helper.setFrom(appEmail);
        helper.setTo(recipientEmail);
        helper.setSubject("Verify your email address");
        helper.setText(html, true);

        mailSender.send(mime);
        log.info("Verification email sent to {}", recipientEmail);
    }

    @Async
    public void sendPasswordResetEmail(String recipientEmail, String firstName, String resetUrl) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            Context ctx = new Context();
            ctx.setVariable("firstName", firstName);
            ctx.setVariable("resetUrl", resetUrl);

            String html = templateEngine.process("PasswordReset", ctx);

            helper.setFrom(appEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("Reset your password");
            helper.setText(html, true);

            mailSender.send(mime);
            log.info("Password reset email sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage());
        }
    }

    @Async
    public void sendTamperingAlert(String recipientEmail, String recipientName,
                                   String severity, String summary) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(appEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("[" + severity + "] Vote Tampering Detected");

            // HTML-escape dynamic values to prevent HTML injection in outbound email.
            // summary is plain text (newline-delimited); convert \n to <br/> after escaping.
            String safeName = org.springframework.web.util.HtmlUtils.htmlEscape(recipientName);
            String safeSummary = org.springframework.web.util.HtmlUtils.htmlEscape(summary)
                    .replace("\n", "<br/>");
            String headerColor = "CRITICAL".equals(severity) ? "#c0392b" : "#e67e22";

            String body = "<html><body style='font-family:sans-serif'>"
                    + "<h2 style='color:" + headerColor + "'>"
                    + severity + ": Vote Tampering Detected</h2>"
                    + "<p>Hi " + safeName + ",</p>"
                    + "<p>" + safeSummary + "</p>"
                    + "<p>Log in to the admin panel immediately to review the audit trail.</p>"
                    + "<hr/><small>This is an automated security alert from the Election Platform.</small>"
                    + "</body></html>";

            helper.setText(body, true);
            mailSender.send(mime);
            log.warn("Tampering alert sent to {} [{}]", recipientEmail, severity);
        } catch (Exception e) {
            log.error("Failed to send tampering alert to {}: {}", recipientEmail, e.getMessage());
        }
    }

    /**
     * Send OTP via SMS.
     * Stubbed with a log until an SMS gateway (e.g. Twilio, Africa's Talking) is wired in.
     * Replace the body with the provider's SDK call and add credentials to application.properties.
     */
    @Async
    public void sendOtpSms(String phoneNumber, String recipientName, String otp) {
        // TODO: integrate SMS provider (Twilio, Africa's Talking, etc.)
        // Example Twilio call:
        //   Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(smsFromNumber),
        //       "Hi " + recipientName + ", your verification code is: " + otp)
        //       .create();
        // OTPs are authentication secrets — never log the value itself.
        log.info("[SMS-STUB] OTP requested for {} ({}) — wire up an SMS gateway to deliver this",
                recipientName, phoneNumber);
    }

    /**
     * Sends an admin-provisioned account welcome email containing the user's
     * temporary credentials and a prompt to change their password on first login.
     */
    @Async
    public void sendWelcomeEmail(String recipientEmail, String firstName,
                                 String createdByName, String organizationName,
                                 String role, String tempPassword, String loginUrl) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            Context ctx = new Context();
            ctx.setVariable("firstName", firstName);
            ctx.setVariable("email", recipientEmail);
            ctx.setVariable("createdByName", createdByName);
            ctx.setVariable("organizationName", organizationName);
            ctx.setVariable("role", role);
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("loginUrl", loginUrl);

            String html = templateEngine.process("UserWelcome", ctx);

            helper.setFrom(appEmail);
            helper.setTo(recipientEmail);
            helper.setSubject("Your account has been created — " + organizationName);
            helper.setText(html, true);

            mailSender.send(mime);
            log.info("Welcome email sent to {} (provisioned by {})", recipientEmail, createdByName);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", recipientEmail, e.getMessage());
        }
    }

    @Async
    public void sendOtpEmail(String recipientEmail, String recipientName, String otp) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper =
                new MimeMessageHelper(mime, true, "UTF-8");

        Context ctx = new Context();
        ctx.setVariable("recipient_name", recipientName);
        ctx.setVariable("otp", otp);


        String html = templateEngine.process("OtpEmail", ctx);

        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        helper.setFrom(appEmail);
        helper.setTo(recipientEmail);
        helper.setSubject("Your verification code");
        helper.setText(text, html);

        mailSender.send(mime);
    }
}