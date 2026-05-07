package com.dosys.platform.medication.interfaces.rest.dto.response;

public record EdgeCredentialsResponse(
        Long deviceId,
        String deviceKey
) {
}
