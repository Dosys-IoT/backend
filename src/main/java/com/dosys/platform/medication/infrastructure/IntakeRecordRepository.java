package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.IntakeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IntakeRecordRepository extends JpaRepository<IntakeRecord, Long> {
    List<IntakeRecord> findByDeviceIdAndScheduledAtBetweenOrderByScheduledAtAsc(Long deviceId, OffsetDateTime from, OffsetDateTime to);
    Optional<IntakeRecord> findByDeviceIdAndScheduleIdAndScheduledAt(Long deviceId, Long scheduleId, OffsetDateTime scheduledAt);
    Optional<IntakeRecord> findByDeviceIdAndEventId(Long deviceId, String eventId);
}
