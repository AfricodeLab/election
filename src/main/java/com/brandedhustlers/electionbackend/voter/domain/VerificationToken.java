package com.brandedhustlers.electionbackend.voter.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
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
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID electionId;

    @OneToOne
    @JoinColumn(name = "voter_id")
    private Voter voter;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private String type; //sms or otp

    @Column(nullable = false)
    private Timestamp expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Timestamp createdAt;

}
