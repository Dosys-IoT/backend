package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.DeviceHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceHeartbeatRepository extends JpaRepository<DeviceHeartbeat, Long> {
    Optional<DeviceHeartbeat> findByDeviceIdAndEventId(Long deviceId, String eventId);
    Optional<DeviceHeartbeat> findFirstByDeviceIdOrderByRecordedAtDesc(Long deviceId);
}
