package com.dosys.platform.medication.interfaces.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateAlarmSettingsRequest(
        @NotNull @Min(0) @Max(100) Integer alarmVolumePercent,
        @NotNull Boolean quietHoursEnabled,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String quietHoursStart,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String quietHoursEnd,
        @NotNull @Min(0) @Max(100) Integer quietHoursVolumePercent
) {
}
