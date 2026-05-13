package com.dosys.platform.medication.application;

import com.dosys.platform.access.domain.User;
import com.dosys.platform.access.infrastructure.UserRepository;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.EnvironmentReading;
import com.dosys.platform.medication.domain.EnvironmentRiskStatus;
import com.dosys.platform.medication.domain.IntakeRecord;
import com.dosys.platform.medication.domain.IntakeStatus;
import com.dosys.platform.medication.domain.MedicationContainer;
import com.dosys.platform.medication.domain.MedicationSchedule;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.dosys.platform.medication.infrastructure.EnvironmentReadingRepository;
import com.dosys.platform.medication.infrastructure.IntakeRecordRepository;
import com.dosys.platform.medication.infrastructure.MedicationContainerRepository;
import com.dosys.platform.medication.infrastructure.MedicationScheduleRepository;
import com.dosys.platform.medication.interfaces.rest.dto.request.CreateDeviceRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertContainerRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertScheduleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationServiceUnitTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private MedicationContainerRepository containerRepository;
    @Mock private MedicationScheduleRepository scheduleRepository;
    @Mock private IntakeRecordRepository intakeRecordRepository;
    @Mock private EnvironmentReadingRepository environmentReadingRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MedicationService medicationService;

    private User owner;
    private Device device;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@test.com");

        device = new Device();
        device.setId(100L);
        device.setOwner(owner);
        device.setConfigVersion(1);

    }

    @Test
    void createDeviceAssociatesOwnerAndInitializesFiveContainers() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device d = invocation.getArgument(0);
            d.setId(100L);
            return d;
        });

        medicationService.createDevice("owner@test.com", new CreateDeviceRequest("Home"));

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(deviceCaptor.capture());
        assertThat(deviceCaptor.getValue().getOwner().getId()).isEqualTo(1L);

        ArgumentCaptor<List<MedicationContainer>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(containerRepository).saveAll(listCaptor.capture());
        assertThat(listCaptor.getValue()).hasSize(5);
        assertThat(listCaptor.getValue()).extracting(MedicationContainer::getContainerNumber)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void upsertContainerRejectsContainerNumberOutsideRange() {
        assertThatThrownBy(() -> medicationService.upsertContainer("owner@test.com", 100L, 0,
                new UpsertContainerRequest("A", "B", 1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("containerNumber must be between 1 and 5");

        assertThatThrownBy(() -> medicationService.upsertContainer("owner@test.com", 100L, 6,
                new UpsertContainerRequest("A", "B", 1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("containerNumber must be between 1 and 5");
    }

    @Test
    void upsertContainerRejectsNegativeRemainingPills() {
        assertThatThrownBy(() -> medicationService.upsertContainer("owner@test.com", 100L, 1,
                new UpsertContainerRequest("A", "B", -1, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("remainingPills cannot be negative");
    }

    @Test
    void upsertContainerIncrementsConfigVersionWhenChanged() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        MedicationContainer container = new MedicationContainer();
        container.setId(11L);
        container.setContainerNumber(1);
        container.setMedicationName("Old");
        container.setDosageLabel("Old");
        container.setRemainingPills(10);
        container.setIsEnabled(false);

        when(deviceRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(device));
        when(containerRepository.findByDeviceIdAndContainerNumber(100L, 1)).thenReturn(Optional.of(container));

        medicationService.upsertContainer("owner@test.com", 100L, 1,
                new UpsertContainerRequest("New", "500mg", 20, true));

        assertThat(device.getConfigVersion()).isEqualTo(2);
        verify(deviceRepository).save(device);
    }

    @Test
    void createScheduleRejectsDisabledContainer() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        MedicationContainer disabledContainer = new MedicationContainer();
        disabledContainer.setContainerNumber(1);
        disabledContainer.setIsEnabled(false);

        when(deviceRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(device));
        when(containerRepository.findByDeviceIdAndContainerNumber(100L, 1)).thenReturn(Optional.of(disabledContainer));

        assertThatThrownBy(() -> medicationService.createSchedule("owner@test.com", 100L,
                new UpsertScheduleRequest(1, LocalTime.NOON, Set.of(DayOfWeek.MONDAY), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Container is disabled");
    }

    @Test
    void scheduleOperationsIncrementConfigVersionWhenRuleApplies() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        MedicationContainer enabledContainer = new MedicationContainer();
        enabledContainer.setId(50L);
        enabledContainer.setContainerNumber(1);
        enabledContainer.setIsEnabled(true);

        MedicationSchedule schedule = new MedicationSchedule();
        schedule.setId(77L);
        schedule.setContainer(enabledContainer);
        schedule.setTime(LocalTime.of(8, 0));
        schedule.setDaysOfWeek(Set.of(DayOfWeek.MONDAY));
        schedule.setIsActive(true);

        when(deviceRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(device));
        when(containerRepository.findByDeviceIdAndContainerNumber(100L, 1)).thenReturn(Optional.of(enabledContainer));
        when(scheduleRepository.save(any(MedicationSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.findByIdAndDeviceId(77L, 100L)).thenReturn(Optional.of(schedule));

        medicationService.createSchedule("owner@test.com", 100L,
                new UpsertScheduleRequest(1, LocalTime.of(8, 0), Set.of(DayOfWeek.MONDAY), true));
        medicationService.updateSchedule("owner@test.com", 100L, 77L,
                new UpsertScheduleRequest(1, LocalTime.of(9, 0), Set.of(DayOfWeek.MONDAY), true));
        medicationService.deleteSchedule("owner@test.com", 100L, 77L);

        assertThat(device.getConfigVersion()).isEqualTo(4);
        verify(deviceRepository, times(3)).save(device);
    }

    @Test
    void adherenceCalendarGroupsTakenMissedAndSnoozedStates() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        when(deviceRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(device));

        IntakeRecord taken = intakeRecord(1L, IntakeStatus.TAKEN, "2026-05-03T08:00:00Z");
        IntakeRecord missed = intakeRecord(2L, IntakeStatus.MISSED, "2026-05-03T20:00:00Z");
        IntakeRecord snoozed = intakeRecord(3L, IntakeStatus.SNOOZED, "2026-05-04T08:00:00Z");
        when(intakeRecordRepository.findByDeviceIdAndScheduledAtBetweenOrderByScheduledAtAsc(any(), any(), any()))
                .thenReturn(List.of(taken, missed, snoozed));

        var response = medicationService.getAdherenceCalendar("owner@test.com", 100L, "2026-05");

        assertThat(response.days()).hasSize(2);
        assertThat(response.days().get(0).items()).extracting(i -> i.status().name()).contains("TAKEN", "MISSED");
        assertThat(response.days().get(1).items()).extracting(i -> i.status().name()).contains("SNOOZED");
    }

    @Test
    void latestEnvironmentReturnsMappedRiskLevels() {
        when(userRepository.findByEmailIgnoreCase("owner@test.com")).thenReturn(Optional.of(owner));
        when(deviceRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(device));
        EnvironmentReading reading = new EnvironmentReading();
        reading.setId(9L);
        reading.setTemperature(31.0);
        reading.setHumidity(72.0);
        reading.setRecordedAt(OffsetDateTime.parse("2026-05-03T10:00:00Z"));
        reading.setRiskStatus(EnvironmentRiskStatus.CRITICAL);

        when(environmentReadingRepository.findFirstByDeviceIdOrderByRecordedAtDesc(100L)).thenReturn(Optional.of(reading));

        var response = medicationService.getLatestEnvironment("owner@test.com", 100L);

        assertThat(response.riskStatus()).isEqualTo(EnvironmentRiskStatus.CRITICAL);
    }

    private IntakeRecord intakeRecord(Long scheduleId, IntakeStatus status, String scheduledAt) {
        IntakeRecord record = new IntakeRecord();
        record.setScheduleId(scheduleId);
        record.setContainerNumber(1);
        record.setScheduledAt(OffsetDateTime.parse(scheduledAt));
        record.setStatus(status);
        return record;
    }
}
