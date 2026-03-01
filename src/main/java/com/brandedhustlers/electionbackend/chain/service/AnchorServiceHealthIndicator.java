package com.brandedhustlers.electionbackend.chain.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically invoked by Spring Boot Actuator (/actuator/health)
 * or Resilience4j circuit breakers to verify the Anchor Service is online.
 */
@Slf4j
@Component
public class AnchorServiceHealthIndicator implements HealthIndicator {

    private final BlockchainProvider blockchainProvider;

    public AnchorServiceHealthIndicator(BlockchainProvider blockchainProvider) {
        this.blockchainProvider = blockchainProvider;
    }

    @Override
    public Health health() {
        try {
            boolean isOnline = blockchainProvider.ping();
            if (isOnline) {
                return Health.up().withDetail("blockchain_anchor_service", "Reachable and Online").build();
            } else {
                return Health.down().withDetail("blockchain_anchor_service", "Unreachable or connection failed")
                        .build();
            }
        } catch (Exception e) {
            log.error("Error checking Fabric Anchor service health", e);
            return Health.down(e).withDetail("blockchain_anchor_service", "Error during health check").build();
        }
    }
}
