package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record EnvironmentReadingRequest(
        @NotNull Double temperature,
        @NotNull Double humidity,
        @NotNull OffsetDateTime recordedAt
) {
}
