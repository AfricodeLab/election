package com.brandedhustlers.electionbackend.common.config.Fabric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ChainClient {
    private final org.hyperledger.fabric.gateway.Contract contract;
    @Value("${fabric.contractName}")
    String contractName;

    public ChainClient(org.hyperledger.fabric.gateway.Contract contract) {
        this.contract = contract;
    }

    public String anchorBatch(String electionId, String orgId, String merkleRoot, String timestampIso) {
        try {
            var fn = contractName + ":AnchorBatch";
            var res = contract.submitTransaction(fn, electionId, orgId, merkleRoot, timestampIso);
            return new String(res, java.nio.charset.StandardCharsets.UTF_8); // returns batchId
        } catch (Exception e) {
            throw new RuntimeException("Fabric AnchorBatch failed", e);
        }
    }

    public Optional<String> getLatest(String electionId) {
        try {
            var fn = contractName + ":GetLatestBatch";
            var res = contract.evaluateTransaction(fn, electionId);
            return (res == null || res.length == 0) ? Optional.empty()
                    : Optional.of(new String(res, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Fabric GetLatestBatch failed", e);
        }
    }
}
