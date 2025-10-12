package com.brandedhustlers.electionbackend.auth.dto;

import com.brandedhustlers.electionbackend.auth.domain.Role;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AppUserDTO(
        UUID appUserId,
        String firstName,
        String lastName,
        String appUserEmail,
        String organizationName,
        String username,
        String password,
        UserRole userRole,
        Map<String, String> userPreferences,
        Set<Role> roles,
        DeleteYn deleteYn,
        Timestamp createdAt,
        Timestamp updatedAt
) {
}
