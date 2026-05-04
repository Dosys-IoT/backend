package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record StockEventRequest(
        @NotNull Integer containerNumber,
        @NotNull @Min(0) Integer remainingPills,
        @NotNull OffsetDateTime recordedAt
) {
}
