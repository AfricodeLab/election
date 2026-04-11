
package com.brandedhustlers.electionbackend.chain;

import com.brandedhustlers.electionbackend.chain.service.HttpBlockchainProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Unit tests for HttpBlockchainProvider.
 */

@Disabled("Chain framework moved to different project")
@ExtendWith(MockitoExtension.class)
class HttpBlockchainProviderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private HttpBlockchainProvider httpProvider;

    private String electionId;
    private String orgId;

    @BeforeEach
    void setUp() {
        electionId = UUID.randomUUID().toString();
        orgId = UUID.randomUUID().toString();

        ReflectionTestUtils.setField(httpProvider, "anchorServiceUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(httpProvider, "apiKey", "test-api-key");
    }

    @Test
    @DisplayName("HTTP anchor batch successfully hits endpoint")
    void testAnchorBatch_Success() {
        String expectedTxId = "tx-batch-123";
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/chain/batch"), any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(expectedTxId));

        String result = httpProvider.anchorBatch(electionId, orgId, "merkle123", "timestamp");

        assertEquals(expectedTxId, result);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("HTTP anchor config successfully hits endpoint")
    void testAnchorConfig_Success() {
        String expectedTxId = "tx-config-123";
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/chain/config"), any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(expectedTxId));

        String result = httpProvider.anchorConfig(electionId, orgId, "hash123", "timestamp");

        assertEquals(expectedTxId, result);
    }

    @Test
    @DisplayName("HTTP anchor results successfully hits endpoint")
    void testAnchorResults_Success() {
        String expectedTxId = "tx-results-123";
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/chain/results"), any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(expectedTxId));

        String result = httpProvider.anchorResults(electionId, orgId, "hash123", 0, false, "admin", "timestamp");

        assertEquals(expectedTxId, result);
    }

    @Test
    @DisplayName("HTTP get latest batch connects and returns string")
    void testGetLatestBatch_Success() {
        String jsonResult = "{\"merkleRoot\":\"root123\"}";
        when(restTemplate.getForEntity(contains("/api/v1/chain/batch/latest"), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResult));

        String result = httpProvider.getLatestBatch(electionId);

        assertEquals(jsonResult, result);
    }
}

