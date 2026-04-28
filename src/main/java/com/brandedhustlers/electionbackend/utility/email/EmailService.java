package com.brandedhustlers.electionbackend.utility.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmailService {

    private final Resend resend;

    @Value("${app.email.from}")
    private String fromAddress;

    public EmailService(@Value("${app.email.resend-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    /**
     * Send email to recipient(s)
     * @param recipients the list of voters eligible for that particular election
     * @param subject the subject of the mail
     * @param body the html template or the body
     */
    public void sendEmail(List<String> recipients, String subject, String body) {
        try {
            CreateEmailOptions emailOptions = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(recipients)
                    .subject(subject)
                    .html(body)
                    .build();
            CreateEmailResponse response = resend.emails().send(emailOptions);
            log.info("[EmailService] Email Successfully sent to -> {}  with response id {}", recipients, response.getId());
        }
        catch (Exception e) {
            log.error("[EmailService] Resend Error for {} {}", recipients, e.getMessage());
        }
    }
}
