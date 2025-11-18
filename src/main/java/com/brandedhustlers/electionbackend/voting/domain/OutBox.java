package com.brandedhustlers.electionbackend.voting.domain;

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

    @Column
    private Timestamp publishedAt; // null until published
}

//todo anything in the outbox, that is valid
//todo the payload should also have the voter id to allow for validation
//todo for final results the tally table would be updated,
//beause of the validation of the voter side, the user belongs to the org,
//todo check if I can detect which vote does not match
//todo if the vote does not match what's on the chain,