package com.brandedhustlers.electionbackend.voting.service;

import java.util.UUID;

public interface IVoterIdentityService {
    UUID resolveVoterId(UUID orgId, UUID electionId, String voterToken);
}
