package com.dosys.platform.medication.interfaces.rest.dto.response;

public record LinkPhysicalDeviceResponse(
        String deviceId,
        String name,
        Boolean linked,
        String status,
        Long hardwareDeviceId
) {
}
