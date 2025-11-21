package com.brandedhustlers.electionbackend.chain.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        schema = "chain"
)
public class AnchorCursor {
    @Id
    private UUID electionId;
    private Timestamp lastAnchoredAt;
}
