package com.dosys.platform.medication.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record UpsertScheduleRequest(
        @NotNull Integer containerNumber,
        @NotNull LocalTime time,
        @NotEmpty Set<DayOfWeek> daysOfWeek,
        @NotNull Boolean isActive
) {
}
