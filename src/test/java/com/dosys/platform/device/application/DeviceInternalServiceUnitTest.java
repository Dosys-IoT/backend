package com.dosys.platform.device.application;

import com.dosys.platform.device.interfaces.rest.internal.dto.request.EnvironmentReadingRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.HeartbeatRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.IntakeEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.StockEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.RuntimeConfigResponse;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.EnvironmentRiskStatus;
import com.dosys.platform.medication.domain.IntakeRecord;
import com.dosys.platform.medication.domain.IntakeSource;
import com.dosys.platform.medication.domain.IntakeStatus;
import com.dosys.platform.medication.domain.MedicationContainer;
import com.dosys.platform.medication.domain.MedicationSchedule;
import com.dosys.platform.medication.infrastructure.DeviceHeartbeatRepository;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.dosys.platform.medication.infrastructure.DeviceStockEventRepository;
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
import java.time.LocalDateTime;
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
    @Mock private DeviceHeartbeatRepository heartbeatRepository;
    @Mock private DeviceStockEventRepository stockEventRepository;

    @InjectMocks private DeviceInternalService deviceInternalService;

    private Device device;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceInternalService, "edgeServiceKey", "edge-key");

        device = new Device();
        device.setId(100L);
        device.setDeviceKey("device-key");
        device.setConfigVersion(3);

        when(deviceRepository.findById(100L)).thenReturn(Optional.of(device));
    }

    @Test
    void runtimeConfigIncludesDefaultsAndMappedSchedules() {
        MedicationContainer enabled = new MedicationContainer();
        enabled.setContainerNumber(1);
        enabled.setMedicationName("Ibuprofen");
        enabled.setDosageLabel("200mg");
        enabled.setRemainingPills(12);
        enabled.setIsEnabled(true);

        MedicationSchedule active = new MedicationSchedule();
        active.setId(1L);
        active.setContainer(enabled);
        active.setTime(LocalTime.NOON);
        active.setDaysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        active.setIsActive(true);

        when(containerRepository.findByDeviceIdOrderByContainerNumberAsc(100L)).thenReturn(List.of(enabled));
        when(scheduleRepository.findByDeviceIdOrderByTimeAsc(100L)).thenReturn(List.of(active));

        RuntimeConfigResponse response = deviceInternalService.getRuntimeConfig(100L, null, "edge-key");

        assertThat(response.deviceId()).isEqualTo("100");
        assertThat(response.containers()).hasSize(5);
        assertThat(response.schedules()).hasSize(1);
        assertThat(response.environmentThresholds().temperatureWarning()).isEqualTo(28);
    }

    @Test
    void ingestEnvironmentReadingCalculatesRiskAndPersistsEventId() {
        when(environmentReadingRepository.findByDeviceIdAndEventId(100L, "env-1")).thenReturn(Optional.empty());
        when(environmentReadingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = deviceInternalService.ingestEnvironmentReading(100L, null, "edge-key",
                new EnvironmentReadingRequest("env-1", 32.0, 80.0, LocalDateTime.parse("2026-06-27T12:00:00"), "1.0.0"));

        assertThat(response.riskStatus()).isEqualTo(EnvironmentRiskStatus.CRITICAL);
    }

    @Test
    void intakeEventRejectsUnknownSchedule() {
        when(intakeRecordRepository.findByDeviceIdAndEventId(100L, "intake-1")).thenReturn(Optional.empty());
        when(scheduleRepository.findByIdAndDeviceId(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceInternalService.ingestIntakeEvent(100L, null, "edge-key",
                new IntakeEventRequest( "intake-1", 999L, 1, LocalDateTime.parse("2026-05-04T08:00:00"), null,
                        IntakeStatus.TAKEN, IntakeSource.PHYSICAL_BUTTON, 15)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void intakeEventUpsertsDuplicateIntakeByEventId() {
        IntakeRecord existing = new IntakeRecord();
        existing.setDevice(device);
        existing.setEventId("intake-dup");
        existing.setScheduleId(10L);
        existing.setContainerNumber(1);
        existing.setScheduledAt(OffsetDateTime.parse("2026-05-04T08:00:00Z"));
        existing.setStatus(IntakeStatus.MISSED);
        existing.setSource(IntakeSource.PHYSICAL_BUTTON);
        existing.setButtonPin(15);

        when(intakeRecordRepository.findByDeviceIdAndEventId(100L, "intake-dup")).thenReturn(Optional.of(existing));

        var response = deviceInternalService.ingestIntakeEvent(100L, null, "edge-key",
                new IntakeEventRequest("intake-dup", 10L, 1, LocalDateTime.parse("2026-05-04T08:00:00"), null,
                        IntakeStatus.MISSED, IntakeSource.PHYSICAL_BUTTON, 15));

        assertThat(response.eventId()).isEqualTo("intake-dup");
    }

    @Test
    void stockEventRejectsNegativeStock() {
        when(stockEventRepository.findByDeviceIdAndEventId(100L, "stock-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceInternalService.ingestStockEvent(100L, null, "edge-key",
                new StockEventRequest("stock-1", 1, -1, LocalDateTime.parse("2026-05-04T08:00:00"), "INTAKE_CONFIRMED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("remainingPills cannot be negative");
    }

    @Test
    void heartbeatIsStoredWithDeviceStatus() {
        when(heartbeatRepository.findByDeviceIdAndEventId(100L, "hb-1")).thenReturn(Optional.empty());
        when(heartbeatRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deviceInternalService.ingestHeartbeat(100L, null, "edge-key",
                new HeartbeatRequest("hb-1", LocalDateTime.parse("2026-05-04T08:00:00"), true, true, true, true, true, true, true, 15,
                        180000L, -55, "ONLINE", "1.0.0"));

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
