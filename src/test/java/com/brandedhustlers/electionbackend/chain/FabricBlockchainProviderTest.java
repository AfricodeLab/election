package com.brandedhustlers.electionbackend.chain;

import com.brandedhustlers.electionbackend.chain.service.FabricBlockchainProvider;
import org.hyperledger.fabric.gateway.Contract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BlockchainService - Hyperledger Fabric integration.
 */
@ExtendWith(MockitoExtension.class)
class FabricBlockchainProviderTest {

        @Mock
        private Contract contract;

        @InjectMocks
        private FabricBlockchainProvider blockchainService;

        private String electionId;
        private String orgId;

        @BeforeEach
        void setUp() {
                electionId = UUID.randomUUID().toString();
                orgId = UUID.randomUUID().toString();

                // Set contract name via reflection
                ReflectionTestUtils.setField(blockchainService, "contractName", "AnchorContract");
        }

        @Test
        @DisplayName("Anchor batch successfully - returns transaction ID")
        void testAnchorBatch_Success() throws Exception {
                // Arrange
                String merkleRoot = "abc123def456";
                String timestamp = "2024-01-21T12:00:00Z";
                String expectedTxId = "tx-12345";

                when(contract.submitTransaction(
                                eq("AnchorContract:AnchorBatch"),
                                eq(electionId),
                                eq(orgId),
                                eq(merkleRoot),
                                eq(timestamp))).thenReturn(expectedTxId.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.anchorBatch(electionId, orgId, merkleRoot, timestamp);

                // Assert
                assertNotNull(result);
                assertEquals(expectedTxId, result);
                verify(contract, times(1)).submitTransaction(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Anchor batch fails - throws RuntimeException")
        void testAnchorBatch_FabricError() throws Exception {
                // Arrange
                String merkleRoot = "abc123def456";
                String timestamp = "2024-01-21T12:00:00Z";

                when(contract.submitTransaction(any(), any(), any(), any(), any()))
                                .thenThrow(new RuntimeException("Fabric connection failed"));

                // Act & Assert
                RuntimeException exception = assertThrows(RuntimeException.class,
                                () -> blockchainService.anchorBatch(electionId, orgId, merkleRoot, timestamp));

                assertTrue(exception.getMessage().contains("Fabric AnchorBatch failed"));
        }

        @Test
        @DisplayName("Anchor config successfully - returns transaction ID")
        void testAnchorConfig_Success() throws Exception {
                // Arrange
                String configHash = "sha256hashofconfig";
                String timestamp = "2024-01-21T12:00:00Z";
                String expectedTxId = electionId;

                when(contract.submitTransaction(
                                eq("ConfigContract:AnchorConfig"),
                                eq(electionId),
                                eq(orgId),
                                eq(configHash),
                                eq(timestamp))).thenReturn(expectedTxId.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.anchorConfig(electionId, orgId, configHash, timestamp);

                // Assert
                assertNotNull(result);
                assertEquals(expectedTxId, result);
        }

        @Test
        @DisplayName("Anchor config fails - throws RuntimeException")
        void testAnchorConfig_FabricError() throws Exception {
                // Arrange
                when(contract.submitTransaction(eq("ConfigContract:AnchorConfig"), any(), any(), any(), any()))
                                .thenThrow(new RuntimeException("Network timeout"));

                // Act & Assert
                RuntimeException exception = assertThrows(RuntimeException.class,
                                () -> blockchainService.anchorConfig(electionId, orgId, "hash", "timestamp"));

                assertTrue(exception.getMessage().contains("Config anchor failed"));
        }

        @Test
        @DisplayName("Anchor results successfully - returns transaction ID")
        void testAnchorResults_Success() throws Exception {
                // Arrange
                String resultsHash = "sha256hashofresults";
                int excludedVoteCount = 2;
                boolean forceReleased = false;
                String releasedBy = UUID.randomUUID().toString();
                String timestamp = "2024-01-21T12:00:00Z";

                when(contract.submitTransaction(
                                eq("ReleaseContract:AnchorResults"),
                                eq(electionId),
                                eq(orgId),
                                eq(resultsHash),
                                eq("2"),
                                eq("false"),
                                eq(releasedBy),
                                eq(timestamp))).thenReturn(electionId.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.anchorResults(
                                electionId, orgId, resultsHash, excludedVoteCount,
                                forceReleased, releasedBy, timestamp);

                // Assert
                assertNotNull(result);
                assertEquals(electionId, result);
        }

        @Test
        @DisplayName("Anchor results with force release flag")
        void testAnchorResults_WithForceRelease() throws Exception {
                // Arrange
                String resultsHash = "sha256hashofresults";
                String releasedBy = UUID.randomUUID().toString();
                String timestamp = "2024-01-21T12:00:00Z";

                when(contract.submitTransaction(
                                eq("ReleaseContract:AnchorResults"),
                                eq(electionId),
                                eq(orgId),
                                eq(resultsHash),
                                eq("5"),
                                eq("true"), // Force released
                                eq(releasedBy),
                                eq(timestamp))).thenReturn(electionId.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.anchorResults(
                                electionId, orgId, resultsHash, 5, true, releasedBy, timestamp);

                // Assert
                assertNotNull(result);
        }

        @Test
        @DisplayName("Get latest batch - returns JSON data")
        void testGetLatestBatch_ReturnsData() throws Exception {
                // Arrange
                String batchJson = "{\"merkleRoot\":\"abc123\",\"voteCount\":100}";

                when(contract.evaluateTransaction(
                                eq("AnchorContract:GetLatestBatch"),
                                eq(electionId))).thenReturn(batchJson.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.getLatestBatch(electionId);

                // Assert
                assertNotNull(result);
                assertEquals(batchJson, result);
        }

        @Test
        @DisplayName("Get latest batch - returns null when not found")
        void testGetLatestBatch_NotFound() throws Exception {
                // Arrange
                when(contract.evaluateTransaction(any(), any()))
                                .thenReturn(new byte[0]);

                // Act
                String result = blockchainService.getLatestBatch(electionId);

                // Assert
                assertNull(result);
        }

        @Test
        @DisplayName("Get latest batch - returns null on error")
        void testGetLatestBatch_Error() throws Exception {
                // Arrange
                when(contract.evaluateTransaction(any(), any()))
                                .thenThrow(new RuntimeException("Query failed"));

                // Act
                String result = blockchainService.getLatestBatch(electionId);

                // Assert - should return null, not throw
                assertNull(result);
        }

        @Test
        @DisplayName("Get config - returns JSON data")
        void testGetConfig_ReturnsData() throws Exception {
                // Arrange
                String configJson = "{\"configHash\":\"sha256...\",\"timestamp\":\"2024-01-21T12:00:00Z\"}";

                when(contract.evaluateTransaction(
                                eq("ConfigContract:GetConfig"),
                                eq(electionId))).thenReturn(configJson.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.getConfig(electionId);

                // Assert
                assertNotNull(result);
                assertEquals(configJson, result);
        }

        @Test
        @DisplayName("Get results - returns JSON data")
        void testGetResults_ReturnsData() throws Exception {
                // Arrange
                String resultsJson = "{\"resultsHash\":\"sha256...\",\"excludedCount\":0}";

                when(contract.evaluateTransaction(
                                eq("ReleaseContract:GetResults"),
                                eq(electionId))).thenReturn(resultsJson.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.getResults(electionId);

                // Assert
                assertNotNull(result);
                assertEquals(resultsJson, result);
        }

        @Test
        @DisplayName("List batches - returns array JSON")
        void testListBatches_ReturnsData() throws Exception {
                // Arrange
                String batchesJson = "[{\"id\":1},{\"id\":2}]";

                when(contract.evaluateTransaction(
                                eq("AnchorContract:ListBatches"),
                                eq(electionId))).thenReturn(batchesJson.getBytes(StandardCharsets.UTF_8));

                // Act
                String result = blockchainService.listBatches(electionId);

                // Assert
                assertNotNull(result);
                assertEquals(batchesJson, result);
        }

        @Test
        @DisplayName("List batches - returns empty array on error")
        void testListBatches_Error() throws Exception {
                // Arrange
                when(contract.evaluateTransaction(any(), any()))
                                .thenThrow(new RuntimeException("Query failed"));

                // Act
                String result = blockchainService.listBatches(electionId);

                // Assert
                assertEquals("[]", result);
        }
}
