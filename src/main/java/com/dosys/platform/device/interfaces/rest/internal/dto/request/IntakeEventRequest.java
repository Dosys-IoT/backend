package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import com.dosys.platform.medication.domain.IntakeSource;
import com.dosys.platform.medication.domain.IntakeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record IntakeEventRequest(
        @NotBlank String eventId,
        @NotNull Long scheduleId,
        @NotNull Integer containerNumber,
        @NotNull LocalDateTime scheduledAt,
        LocalDateTime confirmedAt,
        @NotNull IntakeStatus status,
        @NotNull IntakeSource source,
        @NotNull Integer buttonPin
) {
}
