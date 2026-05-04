package com.dosys.platform.medication.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceRequest(
        @NotBlank String name
) {
}
