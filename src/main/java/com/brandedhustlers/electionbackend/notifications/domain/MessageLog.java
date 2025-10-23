package com.brandedhustlers.electionbackend.notifications.domain;

import com.brandedhustlers.electionbackend.notifications.enums.Channel;
import com.brandedhustlers.electionbackend.notifications.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * notif_message_log — Lifecycle of each message; stores templateKey+version and a render hash, not the body.
 * Rationale: auditability without storing sensitive content/OTP.
 */
@Entity
@Table(name = "message_log", schema = "notif",
        indexes = {
                @Index(name = "idx_msg_status_time", columnList = "status, created_at"),
                @Index(name = "idx_msg_org_channel", columnList = "org_id, channel"),
                @Index(name = "idx_msg_template_version", columnList = "template_key, template_version")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageLog {
    @Id
    @Column(name = "message_id")
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String toAddress; // mask on read in controller

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "render_hash")
    private String renderHash; // sha256 of rendered body

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}