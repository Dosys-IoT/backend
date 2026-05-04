package com.dosys.platform.medication.interfaces.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertContainerRequest(
        @Size(max = 150) String medicationName,
        @Size(max = 150) String dosageLabel,
        @NotNull @Min(0) Integer remainingPills,
        @NotNull Boolean isEnabled
) {
}
