package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record HeartbeatRequest(
        @NotBlank String eventId,
        @NotNull LocalDateTime rtcTime,
        @NotNull Boolean wifiConnected,
        @NotNull Boolean mqttConnected,
        @NotNull Boolean rtcOk,
        @NotNull Boolean sht3xOk,
        @NotNull Boolean dfPlayerOk,
        @NotNull Boolean sdCardOk,
        @NotNull Boolean switchOk,
        @NotNull Integer buttonPin,
        @NotNull Long freeHeap,
        @NotNull Integer rssi,
        @NotBlank String deviceStatus,
        @NotBlank String firmwareVersion
) {
}
