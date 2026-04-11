package com.brandedhustlers.electionbackend.voter.domain;

import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "verification_token",
        schema = "voter"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID voterId;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private String type; //magic URL or otp

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private String channel;

    @Column(nullable = true)
    private boolean isUsed;

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn;

    @PrePersist
    void onCreate(){
        deleteYn = DeleteYn.N;
    }

}
