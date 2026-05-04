package com.dosys.platform.device.interfaces.rest.internal.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record RuntimeConfigResponse(
        Long deviceId,
        Integer configVersion,
        OffsetDateTime serverTime,
        String timezone,
        Double humidityThreshold,
        Double temperatureThreshold,
        List<RuntimeContainer> containers,
        List<RuntimeSchedule> activeSchedules
) {
    public record RuntimeContainer(Integer containerNumber, String medicationName, String dosageLabel, Integer remainingPills) {}
    public record RuntimeSchedule(Long scheduleId, Integer containerNumber, LocalTime time, Set<DayOfWeek> daysOfWeek) {}
}
