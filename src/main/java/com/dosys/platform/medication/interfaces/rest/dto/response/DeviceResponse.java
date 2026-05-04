package com.dosys.platform.medication.interfaces.rest.dto.response;

import java.time.OffsetDateTime;

public record DeviceResponse(
        Long id,
        String name,
        Integer configVersion,
        Double humidityThreshold,
        Double temperatureThreshold,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
