package com.dosys.platform.device.interfaces.rest.internal.dto.response;

public record HeartbeatResponse(
        String eventId,
        String deviceId,
        String status,
        String recordedAt
) {
}
