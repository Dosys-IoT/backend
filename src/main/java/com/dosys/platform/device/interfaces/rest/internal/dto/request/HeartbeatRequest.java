package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record HeartbeatRequest(
        @NotNull OffsetDateTime recordedAt,
        @NotNull OffsetDateTime rtcTime,
        @NotNull Boolean wifiConnected,
        @NotBlank String deviceStatus
) {
}
