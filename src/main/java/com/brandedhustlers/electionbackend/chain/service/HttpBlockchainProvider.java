package com.brandedhustlers.electionbackend.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "blockchain.provider", havingValue = "http")
public class HttpBlockchainProvider implements BlockchainProvider {

    private final RestTemplate restTemplate;

    @Value("${blockchain.anchor.service.url}")
    private String anchorServiceUrl;

    @Value("${blockchain.anchor.service.api-key}")
    private String apiKey;

    public HttpBlockchainProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey);
        return headers;
    }

    @Override
    public String anchorBatch(String electionId, String orgId, String merkleRoot, String timestampIso) {
        Map<String, String> payload = new HashMap<>();
        payload.put("electionId", electionId);
        payload.put("orgId", orgId);
        payload.put("merkleRoot", merkleRoot);
        payload.put("timestampIso", timestampIso);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    anchorServiceUrl + "/api/v1/chain/batch",
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to call Anchor Microservice for batch", e);
            throw new RuntimeException("Anchor service call failed", e);
        }
    }

    @Override
    public String anchorConfig(String electionId, String orgId, String configHash, String timestampIso) {
        Map<String, String> payload = new HashMap<>();
        payload.put("electionId", electionId);
        payload.put("orgId", orgId);
        payload.put("configHash", configHash);
        payload.put("timestampIso", timestampIso);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    anchorServiceUrl + "/api/v1/chain/config",
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to call Anchor Microservice for config", e);
            throw new RuntimeException("Anchor service call failed", e);
        }
    }

    @Override
    public String anchorResults(String electionId, String orgId, String resultsHash, int excludedVoteCount,
            boolean forceReleased, String releasedBy, String timestampIso) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("electionId", electionId);
        payload.put("orgId", orgId);
        payload.put("resultsHash", resultsHash);
        payload.put("excludedVoteCount", excludedVoteCount);
        payload.put("forceReleased", forceReleased);
        payload.put("releasedBy", releasedBy);
        payload.put("timestampIso", timestampIso);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, getHeaders());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    anchorServiceUrl + "/api/v1/chain/results",
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to call Anchor Microservice for results", e);
            throw new RuntimeException("Anchor service call failed", e);
        }
    }

    @Override
    public String getLatestBatch(String electionId) {
        HttpEntity<?> request = new HttpEntity<>(getHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    anchorServiceUrl + "/api/v1/chain/batch/latest?electionId=" + electionId,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get latest batch from Anchor Microservice", e);
            return null;
        }
    }

    @Override
    public String getConfig(String electionId) {
        HttpEntity<?> request = new HttpEntity<>(getHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    anchorServiceUrl + "/api/v1/chain/config?electionId=" + electionId,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get config from Anchor Microservice", e);
            return null;
        }
    }

    @Override
    public String getResults(String electionId) {
        HttpEntity<?> request = new HttpEntity<>(getHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    anchorServiceUrl + "/api/v1/chain/results?electionId=" + electionId,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get results from Anchor Microservice", e);
            return null;
        }
    }

    @Override
    public String listBatches(String electionId) {
        HttpEntity<?> request = new HttpEntity<>(getHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    anchorServiceUrl + "/api/v1/chain/batch/list?electionId=" + electionId,
                    org.springframework.http.HttpMethod.GET,
                    request,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to list batches from Anchor Microservice", e);
            return "[]";
        }
    }

    @Override
    public boolean ping() {
        HttpEntity<?> request = new HttpEntity<>(getHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    anchorServiceUrl + "/api/v1/chain/ping",
                    org.springframework.http.HttpMethod.GET,
                    request,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Fabric Anchor Service ping failed: {}", e.getMessage());
            return false;
        }
    }
}
