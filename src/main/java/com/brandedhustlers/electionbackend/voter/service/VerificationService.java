package com.brandedhustlers.electionbackend.voter.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VerificationService implements IVerificationService{
    @Override
    public void sendOtp(UUID orgId, UUID electionId, String phoneOrVoterId) {

    }

    @Override
    public boolean confirmOtp(UUID orgId, UUID electionId, String phoneOrVoterId, String token) {
        return false;
    }
}
