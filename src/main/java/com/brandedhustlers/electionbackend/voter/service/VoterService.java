package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VoterService implements IVoterService{
    @Override
    public VoterDTO create(UUID orgId, CreateVoterCmd cmd) {
        return null;
    }

    @Override
    public List<VoterDTO> search(UUID orgId, String q, int limit) {
        return List.of();
    }

    @Override
    public void assignToElection(UUID orgId, AssignVotersCmd cmd) {

    }
}
