package com.dosys.platform.device.application;

import com.dosys.platform.device.interfaces.rest.internal.dto.request.HeartbeatRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.IntakeEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.StockEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.RuntimeConfigResponse;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.IntakeRecord;
import com.dosys.platform.medication.domain.IntakeStatus;
import com.dosys.platform.medication.domain.MedicationContainer;
import com.dosys.platform.medication.domain.MedicationSchedule;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.dosys.platform.medication.infrastructure.EnvironmentReadingRepository;
import com.dosys.platform.medication.infrastructure.IntakeRecordRepository;
import com.dosys.platform.medication.infrastructure.MedicationContainerRepository;
import com.dosys.platform.medication.infrastructure.MedicationScheduleRepository;
import com.dosys.platform.shared.exception.ForbiddenException;
import com.dosys.platform.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceInternalServiceUnitTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private MedicationContainerRepository containerRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private IntakeRecordRepository intakeRecordRepository;
    @Mock private EnvironmentReadingRepository environmentReadingRepository;

    @InjectMocks private DeviceInternalService deviceInternalService;

    private Device device;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceInternalService, "edgeServiceKey", "edge-key");

        device = new Device();
        device.setId(100L);
        device.setDeviceKey("device-key");
        device.setConfigVersion(3);
        device.setHumidityThreshold(70.0);
        device.setTemperatureThreshold(30.0);

        when(deviceRepository.findById(100L)).thenReturn(Optional.of(device));
    }

    @Test
    void runtimeConfigIncludesOnlyEnabledContainersAndActiveSchedules() {
        MedicationContainer enabled = new MedicationContainer();
        enabled.setContainerNumber(1);
        enabled.setIsEnabled(true);
        MedicationContainer disabled = new MedicationContainer();
        disabled.setContainerNumber(2);
        disabled.setIsEnabled(false);

        MedicationSchedule active = new MedicationSchedule();
        active.setId(1L);
        active.setContainer(enabled);
        active.setTime(LocalTime.NOON);
        active.setDaysOfWeek(Set.of(DayOfWeek.MONDAY));
        active.setIsActive(true);

        MedicationSchedule inactive = new MedicationSchedule();
        inactive.setId(2L);
        inactive.setContainer(enabled);
        inactive.setTime(LocalTime.MIDNIGHT);
        inactive.setDaysOfWeek(Set.of(DayOfWeek.TUESDAY));
        inactive.setIsActive(false);

        when(containerRepository.findByDeviceIdOrderByContainerNumberAsc(100L)).thenReturn(List.of(enabled, disabled));
        when(scheduleRepository.findByDeviceIdOrderByTimeAsc(100L)).thenReturn(List.of(active, inactive));

        RuntimeConfigResponse response = deviceInternalService.getRuntimeConfig(100L, "device-key", null);

        assertThat(response.containers()).hasSize(1);
        assertThat(response.activeSchedules()).hasSize(1);
    }

    @Test
    void intakeEventRejectsUnknownSchedule() {
        when(scheduleRepository.findByIdAndDeviceId(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceInternalService.ingestIntakeEvent(100L, "device-key", null,
                new IntakeEventRequest(999L, 1, OffsetDateTime.parse("2026-05-04T08:00:00Z"), null, IntakeStatus.TAKEN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void intakeEventUpsertsDuplicateIntake() {
        MedicationContainer container = new MedicationContainer();
        container.setContainerNumber(1);
        MedicationSchedule schedule = new MedicationSchedule();
        schedule.setId(10L);
        schedule.setContainer(container);

        IntakeRecord existing = new IntakeRecord();

        when(scheduleRepository.findByIdAndDeviceId(10L, 100L)).thenReturn(Optional.of(schedule));
        when(intakeRecordRepository.findByDeviceIdAndScheduleIdAndScheduledAt(any(), any(), any())).thenReturn(Optional.of(existing));

        deviceInternalService.ingestIntakeEvent(100L, "device-key", null,
                new IntakeEventRequest(10L, 1, OffsetDateTime.parse("2026-05-04T08:00:00Z"), null, IntakeStatus.MISSED));

        verify(intakeRecordRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo(IntakeStatus.MISSED);
    }

    @Test
    void stockEventRejectsNegativeStock() {
        assertThatThrownBy(() -> deviceInternalService.ingestStockEvent(100L, "device-key", null,
                new StockEventRequest(1, -1, OffsetDateTime.parse("2026-05-04T08:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("remainingPills cannot be negative");
    }

    @Test
    void heartbeatIsStoredWithDeviceStatus() {
        deviceInternalService.ingestHeartbeat(100L, "device-key", null,
                new HeartbeatRequest(
                        OffsetDateTime.parse("2026-05-04T08:00:00Z"),
                        OffsetDateTime.parse("2026-05-04T08:00:00Z"),
                        true,
                        "ONLINE"));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getLastKnownStatus()).isEqualTo("ONLINE");
    }

    @Test
    void wrongDeviceOrServiceKeyIsRejected() {
        assertThatThrownBy(() -> deviceInternalService.getRuntimeConfig(100L, "wrong", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Invalid device key");

        assertThatThrownBy(() -> deviceInternalService.getRuntimeConfig(100L, null, "wrong"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Invalid edge service key");
    }
}
