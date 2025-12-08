package com.brandedhustlers.electionbackend.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.gateway.Contract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class BlockchainService {

    private final Contract contract;

    @Value("${fabric.contractName}")
    private String contractName;

    public BlockchainService(Contract contract) {
        this.contract = contract;
    }

    /**
     * Anchor vote batch (existing method - keep as is).
     */
    public String anchorBatch(String electionId, String orgId, String merkleRoot, String timestampIso) {
        try {
            var fn = contractName + ":AnchorBatch";
            var res = contract.submitTransaction(fn, electionId, orgId, merkleRoot, timestampIso);
            return new String(res, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to anchor batch", e);
            throw new RuntimeException("Fabric AnchorBatch failed", e);
        }
    }

    /**
     * Anchor election config.
     * Calls ConfigContract:AnchorConfig on the blockchain.
     *
     * @param electionId UUID string
     * @param orgId UUID string
     * @param configHash SHA-256 hash (64 hex chars)
     * @param timestamp ISO-8601 timestamp
     * @return Transaction ID (electionId is returned by chaincode)
     */
    public String anchorConfig(String electionId, String orgId, String configHash, String timestamp) {
        try {
            log.info("Anchoring config: election={}, hash={}", electionId, configHash);

            /**
             * Submit to ConfigContract:AnchorConfig.
             * Chaincode returns the electionId as confirmation.
             */
            byte[] result = contract.submitTransaction(
                    "ConfigContract:AnchorConfig",
                    electionId,
                    orgId,
                    configHash,
                    timestamp
            );

            String txId = new String(result, StandardCharsets.UTF_8);
            log.info("Config anchored: txId={}", txId);

            return txId;

        } catch (Exception e) {
            log.error("Failed to anchor config for election {}", electionId, e);
            throw new RuntimeException("Config anchor failed: " + e.getMessage(), e);
        }
    }

    /**
     * Anchor election results.
     * Calls ReleaseContract:AnchorResults on the blockchain.
     *
     * IMPORTANT: This method signature must match what ReleaseService calls.
     *
     * @param electionId UUID string
     * @param orgId UUID string
     * @param resultsHash SHA-256 hash of results JSON
     * @param excludedVoteCount Number of votes excluded due to tampering
     * @param forceReleased Whether admin overrode validation warnings
     * @param releasedBy Admin user ID who released
     * @param timestamp ISO-8601 timestamp
     * @return Transaction ID (electionId is returned by chaincode)
     */
    public String anchorResults(
            String electionId,
            String orgId,
            String resultsHash,
            int excludedVoteCount,
            boolean forceReleased,
            String releasedBy,
            String timestamp
    ) {
        try {
            log.info("Anchoring results: election={}, hash={}, excluded={}, forced={}",
                    electionId, resultsHash, excludedVoteCount, forceReleased);

            /**
             * Submit to ReleaseContract:AnchorResults.
             * Convert int and boolean to strings for chaincode.
             */
            byte[] result = contract.submitTransaction(
                    "ReleaseContract:AnchorResults",
                    electionId,
                    orgId,
                    resultsHash,
                    String.valueOf(excludedVoteCount),  // Convert int → string
                    String.valueOf(forceReleased),      // Convert boolean → string
                    releasedBy,
                    timestamp
            );

            String txId = new String(result, StandardCharsets.UTF_8);
            log.info("Results anchored: txId={}", txId);

            return txId;

        } catch (Exception e) {
            log.error("Failed to anchor results for election {}", electionId, e);
            throw new RuntimeException("Results anchor failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get latest batch for an election (read-only query).
     */
    public String getLatestBatch(String electionId) {
        try {
            var fn = contractName + ":GetLatestBatch";
            byte[] result = contract.evaluateTransaction(fn, electionId);

            if (result == null || result.length == 0) {
                return null;
            }

            return new String(result, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to get latest batch for election {}", electionId, e);
            return null;
        }
    }

    /**
     * Get config anchor for an election (read-only query).
     */
    public String getConfig(String electionId) {
        try {
            byte[] result = contract.evaluateTransaction(
                    "ConfigContract:GetConfig",
                    electionId
            );

            if (result == null || result.length == 0) {
                return null;
            }

            return new String(result, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to get config for election {}", electionId, e);
            return null;
        }
    }

    /**
     * Get results anchor for an election (read-only query).
     */
    public String getResults(String electionId) {
        try {
            byte[] result = contract.evaluateTransaction(
                    "ReleaseContract:GetResults",
                    electionId
            );

            if (result == null || result.length == 0) {
                return null;
            }

            return new String(result, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to get results for election {}", electionId, e);
            return null;
        }
    }

    /**
     * List all batches for an election (read-only query).
     */
    public String listBatches(String electionId) {
        try {
            var fn = contractName + ":ListBatches";
            byte[] result = contract.evaluateTransaction(fn, electionId);

            return new String(result, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to list batches for election {}", electionId, e);
            return "[]";
        }
    }
}