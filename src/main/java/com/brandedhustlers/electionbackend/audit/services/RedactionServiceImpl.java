package com.brandedhustlers.electionbackend.audit.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RedactionServiceImpl implements RedactionService {
    private final String salt;

    RedactionServiceImpl(@Value("${audit.redaction.salt}") String salt) {
        this.salt = salt;
    }

    @Override
    public String hashIp(String rawIp) {
        return hashNullable(rawIp);
    }

    @Override
    public String hashUa(String rawUa) {
        return hashNullable(rawUa);
    }

    private String hashNullable(String s) {
        if (s == null || s.isBlank()) return null;
        return sha256Hex(salt + ":" + s);
    }

    private static String sha256Hex(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}