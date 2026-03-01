package com.brandedhustlers.electionbackend.chain;

import com.brandedhustlers.electionbackend.chain.service.MockBlockchainProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockBlockchainProvider.
 */
class MockBlockchainProviderTest {

    private MockBlockchainProvider mockProvider;
    private String electionId;
    private String orgId;

    @BeforeEach
    void setUp() {
        mockProvider = new MockBlockchainProvider();
        electionId = UUID.randomUUID().toString();
        orgId = UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("Mock anchor batch generates UUID")
    void testAnchorBatch() {
        String result = mockProvider.anchorBatch(electionId, orgId, "merkle123", "2024-01-21T12:00:00Z");
        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("Mock anchor config generates UUID")
    void testAnchorConfig() {
        String result = mockProvider.anchorConfig(electionId, orgId, "hash123", "2024-01-21T12:00:00Z");
        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("Mock anchor results generates UUID")
    void testAnchorResults() {
        String result = mockProvider.anchorResults(electionId, orgId, "hash123", 0, false, "admin",
                "2024-01-21T12:00:00Z");
        assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("Mock get latest batch returns null")
    void testGetLatestBatch() {
        assertNull(mockProvider.getLatestBatch(electionId));
    }

    @Test
    @DisplayName("Mock get config returns null")
    void testGetConfig() {
        assertNull(mockProvider.getConfig(electionId));
    }

    @Test
    @DisplayName("Mock get results returns null")
    void testGetResults() {
        assertNull(mockProvider.getResults(electionId));
    }

    @Test
    @DisplayName("Mock list batches returns empty array string")
    void testListBatches() {
        assertEquals("[]", mockProvider.listBatches(electionId));
    }
}
