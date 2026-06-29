package com.dosys.platform.medication.interfaces.rest.dto.response;

import java.util.List;

public record ContainerSchedulesResponse(
        String deviceId,
        Integer containerNumber,
        List<ScheduleResponse> schedules
) {
}
