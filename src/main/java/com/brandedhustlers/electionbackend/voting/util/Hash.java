package com.brandedhustlers.electionbackend.voting.util;

import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class Hash {

    private final ObjectMapper objectMapper;
    private final String salt;

    public Hash(ObjectMapper objectMapper, @Value("${vote.hash.salt}") String salt) {
        this.objectMapper = objectMapper;
        this.salt = salt;
    }

    public String hashVote(Vote vote) {
        if (vote == null) {
            return null;
        }
        String hash = sha256Hex(vote);
        log.info("hash for vote {}: {}", vote.getId(), hash);
        return hash;
    }

    public String sha256Hex(Vote vote) {
        try {
            //normalize the vote before hashing
            Vote normalized = Vote.builder()
                    .id(vote.getId())
                    .orgId(vote.getOrgId())
                    .electionId(vote.getElectionId())
                    .candidateId(vote.getCandidateId())
                    .voterId(vote.getVoterId())
                    .deviceId(vote.getDeviceId())
                    .dedupeKey(vote.getDedupeKey())
                    .proofRef(vote.getProofRef())
                    .txId(vote.getTxId())
                    .castAt(null)
                    .build();

            String json = objectMapper.writeValueAsString(normalized);
            String saltedData = json + salt;
            log.debug("Hashing vote: {} (with salt)", vote.getId());

            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(saltedData.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Failed to hash vote {} due to an internal error: {}", vote.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to hash vote: " + vote.getId(), e);
        }
    }
}