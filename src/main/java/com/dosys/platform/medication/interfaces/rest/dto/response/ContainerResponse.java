package com.dosys.platform.medication.interfaces.rest.dto.response;

public record ContainerResponse(
        Long id,
        Integer containerNumber,
        String medicationName,
        String dosageLabel,
        Integer remainingPills,
        Boolean isEnabled
) {
}
