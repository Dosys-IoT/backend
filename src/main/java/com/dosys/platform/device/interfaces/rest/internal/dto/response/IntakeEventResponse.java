package com.dosys.platform.device.interfaces.rest.internal.dto.response;

import com.dosys.platform.medication.domain.IntakeSource;
import com.dosys.platform.medication.domain.IntakeStatus;

public record IntakeEventResponse(
        String eventId,
        String deviceId,
        String scheduleId,
        Integer containerNumber,
        String scheduledAt,
        String confirmedAt,
        IntakeStatus status,
        IntakeSource source,
        Integer buttonPin
) {
}
