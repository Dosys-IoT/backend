package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EnvironmentReadingRequest(
        @NotBlank String eventId,
        @NotNull Double temperature,
        @NotNull Double humidity,
        @NotNull LocalDateTime recordedAt,
        @NotBlank String firmwareVersion
) {
}
