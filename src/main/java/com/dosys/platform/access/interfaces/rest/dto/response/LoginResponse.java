package com.dosys.platform.access.interfaces.rest.dto.response;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserResponse user
) {
}
