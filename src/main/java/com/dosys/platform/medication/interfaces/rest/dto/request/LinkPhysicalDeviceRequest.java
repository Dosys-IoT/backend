package com.dosys.platform.medication.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LinkPhysicalDeviceRequest(
        @NotBlank String deviceId,
        String deviceName,
        String deviceKey
) {
}
