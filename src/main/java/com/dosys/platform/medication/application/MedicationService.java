package com.dosys.platform.medication.application;

import com.dosys.platform.access.domain.User;
import com.dosys.platform.access.infrastructure.UserRepository;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.EnvironmentReading;
import com.dosys.platform.medication.domain.IntakeRecord;
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
import com.dosys.platform.medication.interfaces.rest.dto.response.AdherenceCalendarResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ContainerResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.DeviceResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.EnvironmentReadingResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ScheduleResponse;
import com.dosys.platform.shared.exception.DuplicateResourceException;
import com.dosys.platform.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MedicationService {

    private static final int CONTAINER_COUNT = 5;
    private static final double DEFAULT_HUMIDITY_THRESHOLD = 70.0;
    private static final double DEFAULT_TEMPERATURE_THRESHOLD = 30.0;

    private final DeviceRepository deviceRepository;
    private final MedicationContainerRepository containerRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final IntakeRecordRepository intakeRecordRepository;
    private final EnvironmentReadingRepository environmentReadingRepository;
    private final UserRepository userRepository;

    public MedicationService(DeviceRepository deviceRepository,
                             MedicationContainerRepository containerRepository,
                             MedicationScheduleRepository scheduleRepository,
                             IntakeRecordRepository intakeRecordRepository,
                             EnvironmentReadingRepository environmentReadingRepository,
                             UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.containerRepository = containerRepository;
        this.scheduleRepository = scheduleRepository;
        this.intakeRecordRepository = intakeRecordRepository;
        this.environmentReadingRepository = environmentReadingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DeviceResponse createDevice(String userEmail, CreateDeviceRequest request) {
        User owner = getUserByEmail(userEmail);
        if (deviceRepository.existsByOwner(owner)) {
            throw new DuplicateResourceException("User already has a device");
        }

        Device device = new Device();
        device.setOwner(owner);
        device.setName(request.name().trim());
        device.setConfigVersion(1);
        device.setHumidityThreshold(DEFAULT_HUMIDITY_THRESHOLD);
        device.setTemperatureThreshold(DEFAULT_TEMPERATURE_THRESHOLD);
        device.setDeviceKey(UUID.randomUUID().toString());
        Device savedDevice = deviceRepository.save(device);

        List<MedicationContainer> containers = new ArrayList<>();
        for (int i = 1; i <= CONTAINER_COUNT; i++) {
            MedicationContainer container = new MedicationContainer();
            container.setDevice(savedDevice);
            container.setContainerNumber(i);
            container.setMedicationName(null);
            container.setDosageLabel(null);
            container.setRemainingPills(0);
            container.setIsEnabled(false);
            containers.add(container);
        }
        containerRepository.saveAll(containers);

        return toDeviceResponse(savedDevice);
    }

    @Transactional(readOnly = true)
    public List<ContainerResponse> getContainers(String userEmail, Long deviceId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        return containerRepository.findByDeviceIdOrderByContainerNumberAsc(device.getId()).stream().map(this::toContainerResponse).toList();
    }

    @Transactional
    public ContainerResponse upsertContainer(String userEmail, Long deviceId, Integer containerNumber, UpsertContainerRequest request) {
        validateContainerNumber(containerNumber);
        if (request.remainingPills() < 0) {
            throw new IllegalArgumentException("remainingPills cannot be negative");
        }

        Device device = getOwnedDevice(userEmail, deviceId);
        MedicationContainer container = containerRepository.findByDeviceIdAndContainerNumber(deviceId, containerNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));

        boolean changed = !Objects.equals(normalize(container.getMedicationName()), normalize(request.medicationName()))
                || !Objects.equals(normalize(container.getDosageLabel()), normalize(request.dosageLabel()))
                || !Objects.equals(container.getRemainingPills(), request.remainingPills())
                || !Objects.equals(container.getIsEnabled(), request.isEnabled());

        container.setMedicationName(normalize(request.medicationName()));
        container.setDosageLabel(normalize(request.dosageLabel()));
        container.setRemainingPills(request.remainingPills());
        container.setIsEnabled(request.isEnabled());
        containerRepository.save(container);

        if (changed) {
            device.setConfigVersion(device.getConfigVersion() + 1);
            deviceRepository.save(device);
        }

        return toContainerResponse(container);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(String userEmail, Long deviceId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        return scheduleRepository.findByDeviceIdOrderByTimeAsc(device.getId()).stream().map(this::toScheduleResponse).toList();
    }

    @Transactional
    public ScheduleResponse createSchedule(String userEmail, Long deviceId, UpsertScheduleRequest request) {
        Device device = getOwnedDevice(userEmail, deviceId);
        MedicationContainer container = getEnabledContainer(deviceId, request.containerNumber());

        MedicationSchedule schedule = new MedicationSchedule();
        schedule.setDevice(device);
        schedule.setContainer(container);
        schedule.setTime(request.time());
        schedule.setDaysOfWeek(request.daysOfWeek());
        schedule.setIsActive(request.isActive());
        MedicationSchedule saved = scheduleRepository.save(schedule);

        incrementConfigVersion(device);
        return toScheduleResponse(saved);
    }

    @Transactional
    public ScheduleResponse updateSchedule(String userEmail, Long deviceId, Long scheduleId, UpsertScheduleRequest request) {
        Device device = getOwnedDevice(userEmail, deviceId);
        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(scheduleId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        MedicationContainer container = getEnabledContainer(deviceId, request.containerNumber());

        boolean changed = !Objects.equals(schedule.getContainer().getId(), container.getId())
                || !Objects.equals(schedule.getTime(), request.time())
                || !Objects.equals(schedule.getDaysOfWeek(), request.daysOfWeek())
                || !Objects.equals(schedule.getIsActive(), request.isActive());

        schedule.setContainer(container);
        schedule.setTime(request.time());
        schedule.setDaysOfWeek(request.daysOfWeek());
        schedule.setIsActive(request.isActive());
        scheduleRepository.save(schedule);

        if (changed) {
            incrementConfigVersion(device);
        }

        return toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(String userEmail, Long deviceId, Long scheduleId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(scheduleId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        scheduleRepository.delete(schedule);
        incrementConfigVersion(device);
    }

    @Transactional(readOnly = true)
    public AdherenceCalendarResponse getAdherenceCalendar(String userEmail, Long deviceId, String monthValue) {
        getOwnedDevice(userEmail, deviceId);
        YearMonth month;
        try {
            month = YearMonth.parse(monthValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException("month must follow format YYYY-MM");
        }

        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = month.atEndOfMonth().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);

        List<IntakeRecord> records = intakeRecordRepository.findByDeviceIdAndScheduledAtBetweenOrderByScheduledAtAsc(deviceId, from, to);

        Map<LocalDate, List<AdherenceCalendarResponse.Item>> grouped = new LinkedHashMap<>();
        for (IntakeRecord record : records) {
            LocalDate day = record.getScheduledAt().toLocalDate();
            grouped.computeIfAbsent(day, x -> new ArrayList<>())
                    .add(new AdherenceCalendarResponse.Item(
                            record.getScheduleId(),
                            record.getContainerNumber(),
                            record.getScheduledAt(),
                            record.getConfirmedAt(),
                            record.getStatus()
                    ));
        }

        List<AdherenceCalendarResponse.DayAdherence> days = grouped.entrySet().stream()
                .map(entry -> new AdherenceCalendarResponse.DayAdherence(entry.getKey().toString(), entry.getValue()))
                .toList();

        return new AdherenceCalendarResponse(month.toString(), days);
    }

    @Transactional(readOnly = true)
    public EnvironmentReadingResponse getLatestEnvironment(String userEmail, Long deviceId) {
        getOwnedDevice(userEmail, deviceId);
        return environmentReadingRepository.findFirstByDeviceIdOrderByRecordedAtDesc(deviceId)
                .map(this::toEnvironmentResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentReadingResponse> getEnvironmentHistory(String userEmail, Long deviceId, OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from cannot be after to");
        }
        getOwnedDevice(userEmail, deviceId);
        return environmentReadingRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(deviceId, from, to)
                .stream().map(this::toEnvironmentResponse).toList();
    }

    private MedicationContainer getEnabledContainer(Long deviceId, Integer containerNumber) {
        validateContainerNumber(containerNumber);
        MedicationContainer container = containerRepository.findByDeviceIdAndContainerNumber(deviceId, containerNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));
        if (!Boolean.TRUE.equals(container.getIsEnabled())) {
            throw new IllegalArgumentException("Container is disabled");
        }
        return container;
    }

    private void validateContainerNumber(Integer containerNumber) {
        if (containerNumber == null || containerNumber < 1 || containerNumber > CONTAINER_COUNT) {
            throw new IllegalArgumentException("containerNumber must be between 1 and 5");
        }
    }

    private void incrementConfigVersion(Device device) {
        device.setConfigVersion(device.getConfigVersion() + 1);
        deviceRepository.save(device);
    }

    private Device getOwnedDevice(String userEmail, Long deviceId) {
        User user = getUserByEmail(userEmail);
        return deviceRepository.findByIdAndOwnerId(deviceId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(device.getId(), device.getName(), device.getConfigVersion(), device.getHumidityThreshold(),
                device.getTemperatureThreshold(), device.getCreatedAt(), device.getUpdatedAt());
    }

    private ContainerResponse toContainerResponse(MedicationContainer container) {
        return new ContainerResponse(container.getId(), container.getContainerNumber(), container.getMedicationName(),
                container.getDosageLabel(), container.getRemainingPills(), container.getIsEnabled());
    }

    private ScheduleResponse toScheduleResponse(MedicationSchedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getContainer().getContainerNumber(), schedule.getTime(),
                schedule.getDaysOfWeek(), schedule.getIsActive());
    }

    private EnvironmentReadingResponse toEnvironmentResponse(EnvironmentReading reading) {
        return new EnvironmentReadingResponse(reading.getId(), reading.getTemperature(), reading.getHumidity(),
                reading.getRecordedAt(), reading.getRiskStatus());
    }
}
