package com.brandedhustlers.electionbackend.realtime.gateway;

import com.brandedhustlers.electionbackend.common.flags.Feature;
import com.brandedhustlers.electionbackend.common.flags.FeatureFlagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gateway for all WebSocket/STOMP real-time pushes.
 *
 * <p>Each method accepts the caller's {@code orgId} and gates on the
 * {@code REAL_TIME_RESULTS} feature flag <em>explicitly</em> via
 * {@link FeatureFlagService}. This replaces the previous
 * {@code @RequiresFeature} AOP annotation, which relied on
 * {@code SecurityContextHolder} and therefore silently failed-open for
 * background consumers ({@code @RabbitListener}, {@code @Scheduled}) that
 * run without an authenticated principal.</p>
 *
 * <p>If the feature is disabled for the org, the push is simply skipped — no
 * exception is thrown, so background threads don't abort their main work.</p>
 */
@Slf4j
@Component
public class RealtimeGateway {

    private final SimpMessagingTemplate ws;
    private final FeatureFlagService featureFlags;

    public RealtimeGateway(SimpMessagingTemplate ws, FeatureFlagService featureFlags) {
        this.ws = ws;
        this.featureFlags = featureFlags;
    }

    /**
     * Push a live tally update to all subscribers watching an election.
     *
     * @param orgId      the organization that owns the election
     * @param electionId the election being updated
     * @param payload    the tally payload
     */
    public void sendResultsUpdate(UUID orgId, String electionId, Object payload) {
        if (!featureEnabled(orgId, "sendResultsUpdate")) return;
        ws.convertAndSend("/topic/election/results/" + electionId, payload);
    }

    /**
     * Push a device/station status change to ops subscribers.
     *
     * @param orgId     the organization that owns the device
     * @param stationId the polling station whose device changed state
     * @param payload   the device status payload
     */
    public void sendDeviceStatus(UUID orgId, String stationId, Object payload) {
        if (!featureEnabled(orgId, "sendDeviceStatus")) return;
        ws.convertAndSend("/topic/station/" + stationId, payload);
    }

    /**
     * Push a vote acknowledgement to a specific voter session queue.
     *
     * @param orgId          the organization that owns the election
     * @param destinationKey voter-session-specific queue key
     * @param payload        the vote ack payload
     */
    public void sendVoteAck(UUID orgId, String destinationKey, Object payload) {
        if (!featureEnabled(orgId, "sendVoteAck")) return;
        ws.convertAndSend("/queue/vote-ack/" + destinationKey, payload);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private boolean featureEnabled(UUID orgId, String method) {
        if (orgId == null) {
            log.warn("RealtimeGateway.{}: orgId is null — skipping push (tier check not possible)", method);
            return false;
        }
        if (!featureFlags.isEnabled(orgId, Feature.REAL_TIME_RESULTS)) {
            log.debug("RealtimeGateway.{}: REAL_TIME_RESULTS not enabled for org {} — push suppressed", method, orgId);
            return false;
        }
        return true;
    }
}
