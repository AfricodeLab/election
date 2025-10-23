package com.brandedhustlers.electionbackend.notifications.service;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.exceptions.OrganizationNotFound;
import com.brandedhustlers.electionbackend.auth.repository.OrganizationRepository;
import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.exception.LaunchNotfound;
import com.brandedhustlers.electionbackend.ops.repository.LaunchRepository;
import com.brandedhustlers.electionbackend.voter.domain.Voter;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessagingService {
    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);
    private static final String ELECTION_LIVE_TEMPLATE = "ElectionLive";

    private final JavaMailSender mailSender;
    private final OrganizationRepository organizationRepository;
    private final VoterRepository voterRepository;
    private final TemplateEngine templateEngine;
    private final LaunchRepository launchRepository;

    Dotenv dotenv = Dotenv.configure().load();
    private final String appEmail = dotenv.get("SPRING_MAIL_USERNAME");

    public MessagingService(JavaMailSender mailSender,
                            OrganizationRepository organizationRepository,
                            VoterRepository voterRepository,
                            TemplateEngine templateEngine,
                            LaunchRepository launchRepository) {
        this.mailSender = mailSender;
        this.organizationRepository = organizationRepository;
        this.voterRepository = voterRepository;
        this.templateEngine = templateEngine;
        this.launchRepository = launchRepository;
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

            Organization voterOrg = organizationRepository.findById(orgId)
                    .orElseThrow(() -> new OrganizationNotFound("Organization not found with id: " + orgId));

            Launch electionLaunch = launchRepository.findById(launchId)
                    .orElseThrow(() -> new LaunchNotfound("Launch not found with id: " + launchId));

            List<Voter> voters = voterRepository.findByOrganization(voterOrg);
            List<Election> elections = electionLaunch.getElection();

            if (voters.isEmpty()) {
                log.warn("No voters found for organization: {}", orgId);
                return;
            }

            if (elections.isEmpty()) {
                log.warn("No elections found for launch: {}", launchId);
                return;
            }

            log.info("Sending emails to {} voters for {} elections", voters.size(), elections.size());

            int successCount = 0;
            int failureCount = 0;

            for (Election election : elections) {
                String electionTitle = election.getElectionTitle();
                Timestamp startTime = election.getElectionStartTime();
                Timestamp endTime = election.getElectionEndTime();

                for (Voter voter : voters) {
                    try {
                        sendSingleEmail(voter, electionTitle, startTime, endTime);
                        successCount++;
                    } catch (Exception e) {
                        failureCount++;
                        log.error("Failed to send email to voter {} for election {}: {}", voter.getEmail(), electionTitle, e.getMessage(), e);
                    }
                }
            }

            log.info("Email sending completed. Success: {}, Failures: {}", successCount, failureCount);

        } catch (OrganizationNotFound | LaunchNotfound e) {
            log.error("Entity not found: {}", e.getMessage());
            // Don't rethrow - these are business logic errors, not transient failures
        } catch (ClassCastException e) {
            log.error("Invalid payload format: {}", e.getMessage());
            // Payload structure error - no point in retrying
        } catch (Exception e) {
            log.error("Unexpected error processing election notification", e);
            // Rethrow to trigger RabbitMQ retry mechanism for transient failures
            throw new RuntimeException("Failed to process election notification", e);
        }
    }


    @Async
    public void sendSingleEmail(Voter voter, String electionTitle,
                                 Timestamp startTime, Timestamp endTime)
            throws MessagingException {

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        Context context = new Context();
        context.setVariable("voterName", voter.getName());
        context.setVariable("electionName", electionTitle);
        context.setVariable("startTime", startTime);
        context.setVariable("endTime", endTime);

        String htmlContent = templateEngine.process(ELECTION_LIVE_TEMPLATE, context);

        helper.setFrom(appEmail);
        helper.setTo(voter.getEmail());
        helper.setSubject("Election Now Live: " + electionTitle);
        helper.setText(htmlContent, true);

        log.debug("Sending email to {} for election {}", voter.getEmail(), electionTitle);
        mailSender.send(mime);
    }
}