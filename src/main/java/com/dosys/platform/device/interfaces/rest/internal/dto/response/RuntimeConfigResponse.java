package com.dosys.platform.device.interfaces.rest.internal.dto.response;

import java.util.List;

public record RuntimeConfigResponse(
        String deviceId,
        Integer configVersion,
        String serverTime,
        String timezone,
        List<RuntimeContainer> containers,
        List<RuntimeSchedule> schedules,
        EnvironmentThresholds environmentThresholds,
        AlarmSettings alarmSettings
) {
    public record RuntimeContainer(
            Integer containerNumber,
            String medicationName,
            String dosageLabel,
            Integer remainingPills,
            Boolean enabled
    ) {
    }

    public record RuntimeSchedule(
            String scheduleId,
            Integer containerNumber,
            String time,
            List<String> daysOfWeek,
            Integer audioTrack,
            Integer confirmationWindowSeconds
    ) {
    }

    public record EnvironmentThresholds(
            Integer temperatureWarning,
            Integer temperatureCritical,
            Integer humidityWarning,
            Integer humidityCritical
    ) {
    }

    public record AlarmSettings(
            Integer volumePercent,
            Boolean quietHoursEnabled,
            String quietHoursStart,
            String quietHoursEnd,
            Integer quietHoursVolumePercent
    ) {
    }
}
