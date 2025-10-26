package com.brandedhustlers.electionbackend.audit.services;

/**
 * Hashing helpers (salted), small utility to keep concerns isolated
 */
public interface RedactionService {
    String hashIp(String rawIp);

    String hashUa(String rawUa);
}