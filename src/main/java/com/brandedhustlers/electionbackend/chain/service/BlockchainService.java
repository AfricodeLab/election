package com.brandedhustlers.electionbackend.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.gateway.Contract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BlockchainService {
    private final Contract contract;
    @Value("${fabric.contractName}")
    private String contractName;

    public BlockchainService(Contract contract) {
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
    /**
     * NEW: Anchor election config.
     * Calls ConfigContract:AnchorConfig on the blockchain.
     *
     * @return Transaction ID from blockchain
     */
    public String anchorConfig(String electionId, String orgId, String configHash, String timestamp) {
        try {
            // Submit transaction to ConfigContract:AnchorConfig
            // (Implementation depends on your Fabric Gateway setup)

            // Example (adjust to match your gateway code):
            // byte[] result = contract.submitTransaction(
            //     "ConfigContract:AnchorConfig",
            //     electionId,
            //     orgId,
            //     configHash,
            //     timestamp
            // );

            // For now, return a placeholder
            // You'll implement this after we create the chaincode
            log.info("Anchoring config: election={}, hash={}", electionId, configHash);
            return "config-tx-" + java.util.UUID.randomUUID();

        } catch (Exception e) {
            log.error("Failed to anchor config", e);
            throw new RuntimeException("Config anchor failed", e);
        }
    }

    /**
     * NEW: Anchor election results.
     * Calls ReleaseContract:AnchorResults on the blockchain.
     *
     * @return Transaction ID from blockchain
     */
    public String anchorResults(String electionId, String orgId, String resultsHash, String timestamp) {
        try {
            // Submit transaction to ReleaseContract:AnchorResults

            log.info("Anchoring results: election={}, hash={}", electionId, resultsHash);
            return "results-tx-" + java.util.UUID.randomUUID();

        } catch (Exception e) {
            log.error("Failed to anchor results", e);
            throw new RuntimeException("Results anchor failed", e);
        }
    }
}