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
