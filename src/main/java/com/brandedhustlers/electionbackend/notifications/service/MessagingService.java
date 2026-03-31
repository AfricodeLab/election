package com.brandedhustlers.electionbackend.notifications.service;

import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.voter.domain.EligibleVoterElection;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.repository.EligibleVoterElectionRepository;
import com.brandedhustlers.electionbackend.voter.repository.VoterRepository;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
    private final EligibleVoterElectionRepository eligibleVoterElectionRepository;

    Dotenv dotenv = Dotenv.configure().load();
    private final String appEmail = dotenv.get("SPRING_MAIL_USERNAME");

    public MessagingService(JavaMailSender mailSender,
                            VoterRepository voterRepository,
                            TemplateEngine templateEngine,
                            LaunchRepository launchRepository,
                            EligibleVoterElectionRepository eligibleVoterElectionRepository) {
        this.mailSender = mailSender;
        this.voterRepository = voterRepository;
        this.templateEngine = templateEngine;
        this.launchRepository = launchRepository;
        this.eligibleVoterElectionRepository = eligibleVoterElectionRepository;
    }

    @RabbitListener(queues = "notif.email")
    public void sendElectionLiveEmail(Map<String, Object> payload) {
        try {
            // Validate and extract payload
            if (payload == null || !payload.containsKey("launchId") || !payload.containsKey("orgId")) {
                log.error("Invalid payload: missing required fields (launchId or orgId)");
                return;
            }

            UUID launchId = UUID.fromString(payload.get("launchId").toString());
            UUID orgId = UUID.fromString(payload.get("orgId").toString());

            if (launchId == null || orgId == null) {
                log.error("Invalid payload: launchId or orgId is null");
                return;
            }

            log.info("Processing election notification for launchId: {}, orgId: {}", launchId, orgId);

            Launch electionLaunch = launchRepository.findById(launchId)
                    .orElseThrow(() -> new LaunchNotfound("Launch not found with id: " + launchId));

            List<Election> elections = electionLaunch.getElection();

            if (elections.isEmpty()) {
                log.warn("No elections found for launch: {}", launchId);
                return;
            }

            // Build election lookup by ID from the launch
            Map<UUID, Election> electionMap = elections.stream()
                    .collect(Collectors.toMap(Election::getElectionId, Function.identity()));

            // Get all distinct eligible voter IDs from pre-computed snapshot
            List<UUID> eligibleVoterIds = eligibleVoterElectionRepository
                    .findDistinctVoterIdsByLaunchId(launchId);

            if (eligibleVoterIds.isEmpty()) {
                log.warn("No eligible voters found for launch: {}", launchId);
                return;
            }

            // Fetch eligible voters in bulk
            List<Voter> voters = voterRepository.findAllById(eligibleVoterIds);

            // Group eligible election IDs by voter
            Map<UUID, Set<UUID>> voterEligibleElections = eligibleVoterElectionRepository
                    .findByLaunchId(launchId)
                    .stream()
                    .collect(Collectors.groupingBy(
                            EligibleVoterElection::getVoterId,
                            Collectors.mapping(EligibleVoterElection::getElectionId, Collectors.toSet())));

            log.info("Sending emails to {} eligible voters for {} elections", voters.size(), elections.size());

            int successCount = 0;
            int failureCount = 0;

            for (Voter voter : voters) {
                Set<UUID> eligibleElectionIds = voterEligibleElections
                        .getOrDefault(voter.getVoterId(), Collections.emptySet());

                List<Election> voterElections = eligibleElectionIds.stream()
                        .map(electionMap::get)
                        .filter(Objects::nonNull)
                        .toList();

                if (voterElections.isEmpty()) {
                    continue;
                }

                List<String> electionTitles = voterElections.stream()
                        .map(Election::getElectionTitle)
                        .toList();

                Timestamp earliestStartTime = voterElections.stream()
                        .map(Election::getElectionStartTime)
                        .min(Timestamp::compareTo)
                        .orElse(null);

                Timestamp latestEndTime = voterElections.stream()
                        .map(Election::getElectionEndTime)
                        .max(Timestamp::compareTo)
                        .orElse(null);

                try {
                    sendSingleEmail(voter, electionTitles, earliestStartTime, latestEndTime);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to send email to voter {}: {}", voter.getEmail(), e.getMessage(), e);
                }
            }

            log.info("Email sending completed. Success: {}, Failures: {}", successCount, failureCount);

        } catch (LaunchNotfound e) {
            log.error("Entity not found: {}", e.getMessage());
        } catch (ClassCastException e) {
            log.error("Invalid payload format: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing election notification", e);
            throw new RuntimeException("Failed to process election notification", e);
        }
    }

    @Async
    public void sendSingleEmail(Voter voter, List<String> electionTitles,
                                Timestamp startTime, Timestamp endTime)
            throws MessagingException {

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        Context context = new Context();
        context.setVariable("voterName", voter.getName());
        context.setVariable("electionTitles", electionTitles);
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

            String body = "<html><body style='font-family:sans-serif'>"
                    + "<h2 style='color:" + ("CRITICAL".equals(severity) ? "#c0392b" : "#e67e22") + "'>"
                    + severity + ": Vote Tampering Detected</h2>"
                    + "<p>Hi " + recipientName + ",</p>"
                    + "<p>" + summary + "</p>"
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

    @Async
    public void sendOtpEmail(String recipientEmail, String recipientName, String otp) throws MessagingException {
        jakarta.mail.internet.MimeMessage mime = mailSender.createMimeMessage();
        org.springframework.mail.javamail.MimeMessageHelper helper =
                new MimeMessageHelper(mime, true, "UTF-8");

        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
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