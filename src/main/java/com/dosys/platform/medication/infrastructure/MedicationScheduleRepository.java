package com.dosys.platform.medication.infrastructure;

import com.dosys.platform.medication.domain.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {
    List<MedicationSchedule> findByDeviceIdOrderByTimeAsc(Long deviceId);
    Optional<MedicationSchedule> findByIdAndDeviceId(Long id, Long deviceId);
}
