package com.dosys.platform.medication.interfaces.rest.dto.response;

import com.dosys.platform.medication.domain.EnvironmentRiskStatus;

import java.time.OffsetDateTime;

public record EnvironmentReadingResponse(
        Long id,
        Double temperature,
        Double humidity,
        OffsetDateTime recordedAt,
        EnvironmentRiskStatus riskStatus
) {
}
