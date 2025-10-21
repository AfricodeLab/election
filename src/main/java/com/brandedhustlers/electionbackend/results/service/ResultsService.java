package com.brandedhustlers.electionbackend.results.service;

import com.brandedhustlers.electionbackend.results.domain.Tally;
import com.brandedhustlers.electionbackend.results.repository.TallyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ResultsService {
    private final TallyRepository repo;
    public ResultsService(TallyRepository repo){ this.repo=repo; }

    public List<Tally> get(UUID orgId, UUID electionId){
        // TODO enforce org scope by joining with election
        return repo.findByIdElectionId(electionId);
    }
}