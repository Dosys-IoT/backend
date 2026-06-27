package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record StockEventRequest(
        @NotBlank String eventId,
        @NotNull Integer containerNumber,
        @NotNull @Min(0) Integer remainingPills,
        @NotNull LocalDateTime reportedAt,
        @NotBlank String reason
) {
}
