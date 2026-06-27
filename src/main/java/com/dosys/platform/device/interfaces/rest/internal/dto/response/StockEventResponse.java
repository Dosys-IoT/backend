package com.dosys.platform.device.interfaces.rest.internal.dto.response;

public record StockEventResponse(
        String eventId,
        String deviceId,
        Integer containerNumber,
        Integer remainingPills,
        String reportedAt,
        String reason
) {
}
