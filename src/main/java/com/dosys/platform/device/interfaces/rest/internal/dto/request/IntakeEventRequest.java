package com.dosys.platform.device.interfaces.rest.internal.dto.request;

import com.dosys.platform.medication.domain.IntakeStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record IntakeEventRequest(
        @NotNull Long scheduleId,
        @NotNull Integer containerNumber,
        @NotNull OffsetDateTime scheduledAt,
        OffsetDateTime confirmedAt,
        @NotNull IntakeStatus status
) {
}
