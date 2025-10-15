package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VoterImportService implements IVoterImportService{
    @Override
    public UUID startImport(UUID orgId, String fileRef) {
        return null;
    }

    @Override
    public ImportStatusDTO status(UUID orgId, UUID jobId) {
        return null;
    }
}
