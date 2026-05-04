package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.MedicationContainer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationContainerRepository extends JpaRepository<MedicationContainer, Long> {
    List<MedicationContainer> findByDeviceIdOrderByContainerNumberAsc(Long deviceId);
    Optional<MedicationContainer> findByDeviceIdAndContainerNumber(Long deviceId, Integer containerNumber);
}
