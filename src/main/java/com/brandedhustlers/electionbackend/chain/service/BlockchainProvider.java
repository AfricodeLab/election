package com.brandedhustlers.electionbackend.chain.service;

public interface BlockchainProvider {

    /**
     * Anchor a batch of votes (Merkle root).
     */
    String anchorBatch(String electionId, String orgId, String merkleRoot, String timestampIso);

    /**
     * Anchor the initial election configuration.
     */
    String anchorConfig(String electionId, String orgId, String configHash, String timestampIso);

    /**
     * Anchor the final election results.
     */
    String anchorResults(
            String electionId,
            String orgId,
            String resultsHash,
            int excludedVoteCount,
            boolean forceReleased,
            String releasedBy,
            String timestampIso);

    /**
     * Read-only: Get the latest batch ID for an election.
     */
    String getLatestBatch(String electionId);

    /**
     * Read-only: Get the config anchor for an election.
     */
    String getConfig(String electionId);

    /**
     * Read-only: Get the results anchor for an election.
     */
    String getResults(String electionId);

    /**
     * Read-only: List all batches for an election.
     */
    String listBatches(String electionId);

    /**
     * Verify network connection to the underlying blockchain provider.
     */
    boolean ping();
}
