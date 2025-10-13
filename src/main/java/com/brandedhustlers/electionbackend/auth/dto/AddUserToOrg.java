package com.brandedhustlers.electionbackend.auth.dto;

import java.util.UUID;

public record AddUserToOrg(
        UUID userId,
        UUID orgId
) {

}
