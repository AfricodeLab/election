package com.brandedhustlers.electionbackend.voting.domain;

import com.brandedhustlers.electionbackend.voting.enums.PublishStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "outbox", schema = "voting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutBox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Timestamp createdAt;

    private String voteHash;

    @Enumerated(EnumType.STRING)
    private PublishStatus publishStatus;

    private Timestamp failedValidationAt;

    @Column
    private Timestamp publishedAt; // null until published

    /**
     * True when this vote has been validated and is waiting to be anchored.
     * Set by outbox scheduler after successful validation.
     */
    @Column(name = "anchor_pending")
    @Builder.Default
    private Boolean anchorPending = false;

    /**
     * True when this vote's hash has been included in a blockchain batch.
     * Set by anchor scheduler after submitting to chain.
     */
    @Column(name = "anchored")
    @Builder.Default
    private Boolean anchored = false;

    /**
     * Links to the Batch that contains this vote's hash.
     */
    @Column(name = "batch_id")
    private UUID batchId;

    /**
     * When this vote was included in a blockchain batch.
     */
    @Column(name = "anchored_at")
    private Timestamp anchoredAt;

    private UUID electionId;
}

