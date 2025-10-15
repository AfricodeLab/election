package com.brandedhustlers.electionbackend.voting.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VoterIdentityService implements IVoterIdentityService{
    @Override
    public UUID resolveVoterId(UUID orgId, UUID electionId, String voterToken) {
        return null;
    }
}
