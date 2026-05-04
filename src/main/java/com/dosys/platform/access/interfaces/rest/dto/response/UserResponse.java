package com.dosys.platform.access.interfaces.rest.dto.response;

import com.dosys.platform.access.domain.Role;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
