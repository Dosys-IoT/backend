package com.dosys.platform.medication.interfaces.rest.dto.response;

import java.time.OffsetDateTime;

public record DeviceResponse(
        Long id,
        Long hardwareDeviceId,
        String deviceKey,
        String name,
        Integer configVersion,
        Double humidityThreshold,
        Double temperatureThreshold,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
