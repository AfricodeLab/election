package com.brandedhustlers.electionbackend.voter.dto;

/**
 * Response from {@code POST /api/v1/verification/in-person/{voterId}/{electionId}}.
 *
 * <p>The {@code stationPin} is a short-lived 6-digit code that the official
 * hands to the voter. The voter enters it (with their email) at the station
 * terminal to obtain a {@code voterToken} and cast their ballot.</p>
 */
public record InPersonVerifyResponse(
        boolean success,
        String stationPin,
        String message) {
}
