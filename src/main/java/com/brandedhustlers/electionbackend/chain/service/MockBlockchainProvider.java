package com.brandedhustlers.electionbackend.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "blockchain.provider", havingValue = "mock", matchIfMissing = true)
public class MockBlockchainProvider implements BlockchainProvider {

    @Override
    public String anchorBatch(String electionId, String orgId, String merkleRoot, String timestampIso) {
        String txId = UUID.randomUUID().toString();
        log.info("[MOCK CHAIN] Anchored batch for election: {}, root: {} -> txId {}", electionId, merkleRoot, txId);
        return txId;
    }

    @Override
    public String anchorConfig(String electionId, String orgId, String configHash, String timestampIso) {
        String txId = UUID.randomUUID().toString();
        log.info("[MOCK CHAIN] Anchored config for election: {}, hash: {} -> txId {}", electionId, configHash, txId);
        return txId;
    }

    @Override
    public String anchorResults(String electionId, String orgId, String resultsHash, int excludedVoteCount,
            boolean forceReleased, String releasedBy, String timestampIso) {
        String txId = UUID.randomUUID().toString();
        log.info("[MOCK CHAIN] Anchored results for election: {}, hash: {} -> txId {}", electionId, resultsHash, txId);
        return txId;
    }

    @Override
    public String getLatestBatch(String electionId) {
        log.info("[MOCK CHAIN] Returning empty batch for election: {}", electionId);
        return null;
    }

    @Override
    public String getConfig(String electionId) {
        log.info("[MOCK CHAIN] Returning empty config for election: {}", electionId);
        return null;
    }

    @Override
    public String getResults(String electionId) {
        log.info("[MOCK CHAIN] Returning empty results for election: {}", electionId);
        return null;
    }

    @Override
    public String listBatches(String electionId) {
        log.info("[MOCK CHAIN] Returning empty batch list for election: {}", electionId);
        return "[]";
    }

    @Override
    public boolean ping() {
        log.debug("[MOCK CHAIN] Ping check successful");
        return true;
    }
}
