package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.DeviceStockEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceStockEventRepository extends JpaRepository<DeviceStockEvent, Long> {
    Optional<DeviceStockEvent> findByDeviceIdAndEventId(Long deviceId, String eventId);
}
