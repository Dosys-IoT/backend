package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.EnvironmentReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EnvironmentReadingRepository extends JpaRepository<EnvironmentReading, Long> {
    Optional<EnvironmentReading> findFirstByDeviceIdOrderByRecordedAtDesc(Long deviceId);
    List<EnvironmentReading> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long deviceId, OffsetDateTime from, OffsetDateTime to);
    Optional<EnvironmentReading> findByDeviceIdAndEventId(Long deviceId, String eventId);
}
