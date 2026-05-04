package com.dosys.platform.medication.interfaces.rest.dto.response;

import com.dosys.platform.medication.domain.IntakeStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdherenceCalendarResponse(
        String month,
        List<DayAdherence> days
) {
    public record DayAdherence(String date, List<Item> items) {}
    public record Item(Long scheduleId, Integer containerNumber, OffsetDateTime scheduledAt, OffsetDateTime confirmedAt, IntakeStatus status) {}
}
