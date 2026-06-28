package com.dosys.platform.medication.interfaces.rest.dto.response;

public record AlarmSettingsResponse(
        String deviceId,
        Integer alarmVolumePercent,
        Boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        Integer quietHoursVolumePercent
) {
}
