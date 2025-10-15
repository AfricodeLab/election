package com.brandedhustlers.electionbackend.voter.service;

import java.util.UUID;

public interface IVerificationService {
    void sendOtp(UUID orgId, UUID electionId, String phoneOrVoterId);
    boolean confirmOtp(UUID orgId, UUID electionId, String phoneOrVoterId, String token);
}
