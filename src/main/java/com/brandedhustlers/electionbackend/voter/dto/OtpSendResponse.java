package com.brandedhustlers.electionbackend.voter.dto;

import java.util.UUID;

public record OtpSendResponse(
        UUID tokenId,
        String delivery
) {
}
