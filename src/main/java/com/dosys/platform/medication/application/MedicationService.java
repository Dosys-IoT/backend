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
import com.dosys.platform.medication.interfaces.rest.dto.request.LinkPhysicalDeviceRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpdateAlarmSettingsRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertContainerRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertScheduleRequest;
import com.dosys.platform.medication.interfaces.rest.dto.response.AdherenceCalendarResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.AlarmSettingsResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ContainerResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.DeviceResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.DeviceStatusResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.EdgeCredentialsResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.EnvironmentReadingResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.LinkPhysicalDeviceResponse;
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
    private static final int DEFAULT_ALARM_VOLUME_PERCENT = 80;
    private static final boolean DEFAULT_QUIET_HOURS_ENABLED = false;
    private static final String DEFAULT_QUIET_HOURS_START = "21:00";
    private static final String DEFAULT_QUIET_HOURS_END = "06:00";
    private static final int DEFAULT_QUIET_HOURS_VOLUME_PERCENT = 50;

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

        Device device = new Device();
        device.setOwner(owner);
        device.setName(resolveDeviceName(request.name()));
        device.setConfigVersion(1);
        device.setHumidityThreshold(DEFAULT_HUMIDITY_THRESHOLD);
        device.setTemperatureThreshold(DEFAULT_TEMPERATURE_THRESHOLD);
        device.setDeviceKey(UUID.randomUUID().toString());
        device.setHardwareDeviceId(null);
        applyDefaultAlarmSettings(device);
        Device savedDevice = deviceRepository.save(device);
        ensureDefaultContainers(savedDevice);

        return toDeviceResponse(savedDevice);
    }

    @Transactional
    public LinkPhysicalDeviceResponse linkPhysicalDevice(String userEmail, LinkPhysicalDeviceRequest request) {
        User owner = getUserByEmail(userEmail);
        Long hardwareDeviceId = parseDeviceId(request.deviceId());

        Device linkedDevice = findDeviceByHardwareOrId(hardwareDeviceId)
                .map(existing -> reconcileLinkedDevice(existing, owner, hardwareDeviceId, request))
                .orElseGet(() -> createLinkedDevice(owner, hardwareDeviceId, request));

        ensureDefaultContainers(linkedDevice);
        return new LinkPhysicalDeviceResponse(
                String.valueOf(displayDeviceId(linkedDevice)),
                linkedDevice.getName(),
                Boolean.TRUE,
                "LINKED",
                linkedDevice.getHardwareDeviceId()
        );
    }

    @Transactional
    public LinkPhysicalDeviceResponse unlinkPhysicalDevice(String userEmail, Long deviceId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        device.setHardwareDeviceId(null);
        device.setLastKnownStatus("OFFLINE");
        deviceRepository.save(device);
        return new LinkPhysicalDeviceResponse(
                String.valueOf(device.getId()),
                device.getName(),
                Boolean.FALSE,
                "UNLINKED",
                null
        );
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevices(String userEmail) {
        User owner = getUserByEmail(userEmail);
        return deviceRepository.findByOwnerIdOrderByCreatedAtAsc(owner.getId()).stream()
                .map(this::toDeviceResponse)
                .toList();
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
        MedicationContainer container = containerRepository.findByDeviceIdAndContainerNumber(device.getId(), containerNumber)
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
        MedicationContainer container = getEnabledContainer(device.getId(), request.containerNumber());

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
        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(scheduleId, device.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        MedicationContainer container = getEnabledContainer(device.getId(), request.containerNumber());

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
        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(scheduleId, device.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        scheduleRepository.delete(schedule);
        incrementConfigVersion(device);
    }

    @Transactional(readOnly = true)
    public AdherenceCalendarResponse getAdherenceCalendar(String userEmail, Long deviceId, String monthValue) {
        Device device = getOwnedDevice(userEmail, deviceId);
        YearMonth month;
        try {
            month = YearMonth.parse(monthValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException("month must follow format YYYY-MM");
        }

        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = month.atEndOfMonth().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);

        List<IntakeRecord> records = intakeRecordRepository.findByDeviceIdAndScheduledAtBetweenOrderByScheduledAtAsc(device.getId(), from, to);

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
        Device device = getOwnedDevice(userEmail, deviceId);
        return environmentReadingRepository.findFirstByDeviceIdOrderByRecordedAtDesc(device.getId())
                .map(this::toEnvironmentResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentReadingResponse> getEnvironmentHistory(String userEmail, Long deviceId, OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from cannot be after to");
        }
        Device device = getOwnedDevice(userEmail, deviceId);
        return environmentReadingRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(device.getId(), from, to)
                .stream().map(this::toEnvironmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public EdgeCredentialsResponse getEdgeCredentials(String userEmail, Long deviceId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        return new EdgeCredentialsResponse(displayDeviceId(device), device.getDeviceKey());
    }

    @Transactional(readOnly = true)
    public DeviceStatusResponse getDeviceStatus(String userEmail, Long deviceId) {
        Device device = getOwnedDevice(userEmail, deviceId);
        return new DeviceStatusResponse(
                String.valueOf(displayDeviceId(device)),
                device.getLastKnownStatus() == null ? "OFFLINE" : device.getLastKnownStatus(),
                device.getLastSeenAt(),
                device.getLastKnownRtcOk(),
                device.getLastKnownSht3xOk(),
                device.getLastKnownDfPlayerOk(),
                device.getLastKnownSdCardOk(),
                device.getLastKnownSwitchOk(),
                device.getLastKnownButtonPin(),
                device.getLastKnownRssi(),
                device.getLastKnownFirmwareVersion(),
                device.getLastKnownHardwareVersion(),
                device.getLastKnownWifiConnected(),
                device.getLastKnownMqttConnected()
        );
    }

    @Transactional
    public AlarmSettingsResponse updateAlarmSettings(String userEmail, Long deviceId, UpdateAlarmSettingsRequest request) {
        Device device = getOwnedDevice(userEmail, deviceId);
        device.setAlarmVolumePercent(request.alarmVolumePercent());
        device.setQuietHoursEnabled(request.quietHoursEnabled());
        device.setQuietHoursStart(request.quietHoursStart());
        device.setQuietHoursEnd(request.quietHoursEnd());
        device.setQuietHoursVolumePercent(request.quietHoursVolumePercent());
        incrementConfigVersion(device);
        return toAlarmSettingsResponse(device);
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
        return findOwnedDevice(user.getId(), deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
    }

    private java.util.Optional<Device> findOwnedDevice(Long userId, Long deviceId) {
        return deviceRepository.findByIdAndOwnerId(deviceId, userId)
                .or(() -> deviceRepository.findByHardwareDeviceIdAndOwnerId(deviceId, userId));
    }

    private java.util.Optional<Device> findDeviceByHardwareOrId(Long deviceId) {
        return deviceRepository.findById(deviceId).or(() -> deviceRepository.findByHardwareDeviceId(deviceId));
    }

    private Device reconcileLinkedDevice(Device device, User owner, Long hardwareDeviceId, LinkPhysicalDeviceRequest request) {
        if (!device.getOwner().getId().equals(owner.getId())) {
            throw new DuplicateResourceException("DEVICE_ALREADY_LINKED");
        }
        if (request.deviceName() != null && !request.deviceName().isBlank()) {
            device.setName(request.deviceName().trim());
        }
        if (!hardwareDeviceId.equals(device.getHardwareDeviceId())) {
            device.setHardwareDeviceId(hardwareDeviceId);
        }
        if (request.deviceKey() != null && !request.deviceKey().isBlank()) {
            device.setDeviceKey(request.deviceKey());
        } else if (device.getDeviceKey() == null) {
            device.setDeviceKey("");
        }
        applyDefaultAlarmSettings(device);
        return deviceRepository.save(device);
    }

    private Device createLinkedDevice(User owner, Long hardwareDeviceId, LinkPhysicalDeviceRequest request) {
        Device device = new Device();
        device.setOwner(owner);
        device.setName(resolveDeviceName(request.deviceName()));
        device.setConfigVersion(1);
        device.setHumidityThreshold(DEFAULT_HUMIDITY_THRESHOLD);
        device.setTemperatureThreshold(DEFAULT_TEMPERATURE_THRESHOLD);
        device.setHardwareDeviceId(hardwareDeviceId);
        device.setDeviceKey(request.deviceKey() == null ? "" : request.deviceKey());
        applyDefaultAlarmSettings(device);
        return deviceRepository.save(device);
    }

    private void ensureDefaultContainers(Device device) {
        if (containerRepository.findByDeviceIdOrderByContainerNumberAsc(device.getId()).size() == CONTAINER_COUNT) {
            return;
        }

        List<MedicationContainer> containers = new ArrayList<>();
        for (int i = 1; i <= CONTAINER_COUNT; i++) {
            MedicationContainer container = new MedicationContainer();
            container.setDevice(device);
            container.setContainerNumber(i);
            container.setMedicationName(null);
            container.setDosageLabel(null);
            container.setRemainingPills(0);
            container.setIsEnabled(false);
            containers.add(container);
        }
        containerRepository.saveAll(containers);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getHardwareDeviceId(),
                device.getDeviceKey(),
                device.getName(),
                device.getConfigVersion(),
                device.getHumidityThreshold(),
                device.getTemperatureThreshold(),
                defaultInteger(device.getAlarmVolumePercent(), DEFAULT_ALARM_VOLUME_PERCENT),
                defaultBoolean(device.getQuietHoursEnabled(), DEFAULT_QUIET_HOURS_ENABLED),
                defaultString(device.getQuietHoursStart(), DEFAULT_QUIET_HOURS_START),
                defaultString(device.getQuietHoursEnd(), DEFAULT_QUIET_HOURS_END),
                defaultInteger(device.getQuietHoursVolumePercent(), DEFAULT_QUIET_HOURS_VOLUME_PERCENT),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    private AlarmSettingsResponse toAlarmSettingsResponse(Device device) {
        return new AlarmSettingsResponse(
                String.valueOf(displayDeviceId(device)),
                defaultInteger(device.getAlarmVolumePercent(), DEFAULT_ALARM_VOLUME_PERCENT),
                defaultBoolean(device.getQuietHoursEnabled(), DEFAULT_QUIET_HOURS_ENABLED),
                defaultString(device.getQuietHoursStart(), DEFAULT_QUIET_HOURS_START),
                defaultString(device.getQuietHoursEnd(), DEFAULT_QUIET_HOURS_END),
                defaultInteger(device.getQuietHoursVolumePercent(), DEFAULT_QUIET_HOURS_VOLUME_PERCENT)
        );
    }

    private String resolveDeviceName(String rawName) {
        String normalized = normalize(rawName);
        if (normalized != null) {
            return normalized;
        }
        return "Dosys Device " + System.currentTimeMillis();
    }

    private void applyDefaultAlarmSettings(Device device) {
        if (device.getAlarmVolumePercent() == null) {
            device.setAlarmVolumePercent(DEFAULT_ALARM_VOLUME_PERCENT);
        }
        if (device.getQuietHoursEnabled() == null) {
            device.setQuietHoursEnabled(DEFAULT_QUIET_HOURS_ENABLED);
        }
        if (device.getQuietHoursStart() == null || device.getQuietHoursStart().isBlank()) {
            device.setQuietHoursStart(DEFAULT_QUIET_HOURS_START);
        }
        if (device.getQuietHoursEnd() == null || device.getQuietHoursEnd().isBlank()) {
            device.setQuietHoursEnd(DEFAULT_QUIET_HOURS_END);
        }
        if (device.getQuietHoursVolumePercent() == null) {
            device.setQuietHoursVolumePercent(DEFAULT_QUIET_HOURS_VOLUME_PERCENT);
        }
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

    private Long displayDeviceId(Device device) {
        return device.getHardwareDeviceId() != null ? device.getHardwareDeviceId() : device.getId();
    }

    private Integer defaultInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long parseDeviceId(String deviceId) {
        try {
            return Long.parseLong(deviceId.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("deviceId must be a numeric value");
        }
    }
}
