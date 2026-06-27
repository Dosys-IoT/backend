package com.dosys.platform.device.interfaces.rest.internal.dto.response;

import com.dosys.platform.medication.domain.EnvironmentRiskStatus;

public record EnvironmentReadingResponse(
        String eventId,
        String deviceId,
        Double temperature,
        Double humidity,
        String recordedAt,
        EnvironmentRiskStatus riskStatus,
        String firmwareVersion
) {
}
