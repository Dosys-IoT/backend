package com.dosys.platform.medication.interfaces.rest.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record ScheduleResponse(
        Long id,
        Integer containerNumber,
        LocalTime time,
        Set<DayOfWeek> daysOfWeek,
        Boolean isActive
) {
}
