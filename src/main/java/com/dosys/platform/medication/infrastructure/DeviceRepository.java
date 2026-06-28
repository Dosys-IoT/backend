package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.access.domain.User;
import com.dosys.platform.medication.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    boolean existsByOwner(User owner);
    java.util.List<Device> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);
    Optional<Device> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Device> findByHardwareDeviceIdAndOwnerId(Long hardwareDeviceId, Long ownerId);
    Optional<Device> findByIdAndDeviceKey(Long id, String deviceKey);
    Optional<Device> findByHardwareDeviceId(Long hardwareDeviceId);
    Optional<Device> findByIdOrHardwareDeviceId(Long id, Long hardwareDeviceId);
}
