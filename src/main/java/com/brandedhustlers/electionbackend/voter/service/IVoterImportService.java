package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.ImportStatusDTO;

import java.util.UUID;

public interface IVoterImportService {
    UUID startImport(String fileRef);
    ImportStatusDTO status(UUID jobId);
}
