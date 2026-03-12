
package com.brandedhustlers.electionbackend.chain;

import com.brandedhustlers.electionbackend.chain.service.MockBlockchainProvider;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for MockBlockchainProvider.
 */

@Disabled("Chain framework moved to different project")
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
        Assertions.assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("Mock anchor config generates UUID")
    void testAnchorConfig() {
        String result = mockProvider.anchorConfig(electionId, orgId, "hash123", "2024-01-21T12:00:00Z");
        Assertions.assertNotNull(result);
        assertDoesNotThrow(() -> UUID.fromString(result));
    }

    @Test
    @DisplayName("Mock anchor results generates UUID")
    void testAnchorResults() {
        String result = mockProvider.anchorResults(electionId, orgId, "hash123", 0, false, "admin",
                "2024-01-21T12:00:00Z");
        Assertions.assertNotNull(result);
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
        Assertions.assertNull(mockProvider.getConfig(electionId));
    }

    @Test
    @DisplayName("Mock get results returns null")
    void testGetResults() {
        Assertions.assertNull(mockProvider.getResults(electionId));
    }

    @Test
    @DisplayName("Mock list batches returns empty array string")
    void testListBatches() {
        Assertions.assertEquals("[]", mockProvider.listBatches(electionId));
    }
}

