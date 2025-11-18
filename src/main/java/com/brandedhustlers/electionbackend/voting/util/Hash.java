package com.brandedhustlers.electionbackend.voting.util;

import com.brandedhustlers.electionbackend.voting.domain.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class Hash {

    public String hashVote(Vote s) {
        if (s == null) return null;
        return sha256Hex(s);
    }

    public static String sha256Hex(Vote vote) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String s = objectMapper.writeValueAsString(vote);
            var md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
