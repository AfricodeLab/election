package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.AssignVotersCmd;
import com.brandedhustlers.electionbackend.voter.dto.CreateVoterCmd;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;

import java.util.List;
import java.util.UUID;

public interface IVoterService {
    VoterDTO create(UUID orgId, CreateVoterCmd cmd);
    VoterDTO getById(UUID orgId, UUID voterId);
    List<VoterDTO> search(UUID orgId, String q, int limit);
    void assignToElection(UUID orgId, AssignVotersCmd cmd);
}
