package com.dosys.platform.device.application;

import com.dosys.platform.device.interfaces.rest.internal.dto.request.EnvironmentReadingRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.HeartbeatRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.IntakeEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.StockEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.EnvironmentReadingResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.HeartbeatResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.IntakeEventResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.RuntimeConfigResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.StockEventResponse;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.DeviceHeartbeat;
import com.dosys.platform.medication.domain.DeviceStockEvent;
import com.dosys.platform.medication.domain.EnvironmentReading;
import com.dosys.platform.medication.domain.EnvironmentRiskStatus;
import com.dosys.platform.medication.domain.IntakeRecord;
import com.dosys.platform.medication.domain.IntakeSource;
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
import com.dosys.platform.shared.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

@Service
public class DeviceInternalService {

    private static final ZoneId DEVICE_TIMEZONE = ZoneId.of("America/Lima");
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final int TEMPERATURE_WARNING = 28;
    private static final int TEMPERATURE_CRITICAL = 32;
    private static final int HUMIDITY_WARNING = 70;
    private static final int HUMIDITY_CRITICAL = 80;
    private static final int CONFIRMATION_WINDOW_SECONDS = 300;
    private static final int DEFAULT_CONTAINER_COUNT = 5;

    private final DeviceRepository deviceRepository;
    private final MedicationContainerRepository containerRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final IntakeRecordRepository intakeRecordRepository;
    private final EnvironmentReadingRepository environmentReadingRepository;
    private final DeviceHeartbeatRepository heartbeatRepository;
    private final DeviceStockEventRepository stockEventRepository;
    private final String edgeServiceKey;

    public DeviceInternalService(DeviceRepository deviceRepository,
                                 MedicationContainerRepository containerRepository,
                                 MedicationScheduleRepository scheduleRepository,
                                 IntakeRecordRepository intakeRecordRepository,
                                 EnvironmentReadingRepository environmentReadingRepository,
                                 DeviceHeartbeatRepository heartbeatRepository,
                                 DeviceStockEventRepository stockEventRepository,
                                 @Value("${app.edge.service-key}") String edgeServiceKey) {
        this.deviceRepository = deviceRepository;
        this.containerRepository = containerRepository;
        this.scheduleRepository = scheduleRepository;
        this.intakeRecordRepository = intakeRecordRepository;
        this.environmentReadingRepository = environmentReadingRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.stockEventRepository = stockEventRepository;
        this.edgeServiceKey = edgeServiceKey;
    }

    @Transactional(readOnly = true)
    public RuntimeConfigResponse getRuntimeConfig(Long deviceId, String deviceKey, String serviceKey) {
        boolean hasServiceKey = serviceKey != null && !serviceKey.isBlank();
        boolean hasDeviceKey = deviceKey != null && !deviceKey.isBlank();
        if (!hasServiceKey && !hasDeviceKey) {
            throw new UnauthorizedException("Missing X-Edge-Service-Key or X-Device-Key");
        }

        if (hasServiceKey) {
            if (!edgeServiceKey.equals(serviceKey)) {
                throw new ForbiddenException("Invalid edge service key");
            }
            Device device = deviceRepository.findById(deviceId).orElse(null);
            if (device == null) {
                return buildDefaultRuntimeConfig(deviceId);
            }
            return buildRuntimeConfig(deviceId, device);
        }

        Device device = authorize(deviceId, deviceKey, serviceKey);
        return buildRuntimeConfig(deviceId, device);
    }

    private RuntimeConfigResponse buildRuntimeConfig(Long deviceId, Device device) {
        Map<Integer, MedicationContainer> containersByNumber = new LinkedHashMap<>();
        containerRepository.findByDeviceIdOrderByContainerNumberAsc(deviceId)
                .forEach(container -> containersByNumber.put(container.getContainerNumber(), container));

        List<RuntimeConfigResponse.RuntimeContainer> runtimeContainers = java.util.stream.IntStream.rangeClosed(1, DEFAULT_CONTAINER_COUNT)
                .mapToObj(containerNumber -> toRuntimeContainer(containersByNumber.get(containerNumber), containerNumber))
                .toList();

        List<RuntimeConfigResponse.RuntimeSchedule> schedules = scheduleRepository.findByDeviceIdOrderByTimeAsc(deviceId)
                .stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getIsActive()))
                .map(this::toRuntimeSchedule)
                .toList();

        return new RuntimeConfigResponse(
                String.valueOf(device.getId()),
                safeInteger(device.getConfigVersion(), 1),
                OffsetDateTime.now(UTC).toString(),
                DEVICE_TIMEZONE.getId(),
                runtimeContainers,
                schedules,
                new RuntimeConfigResponse.EnvironmentThresholds(
                        TEMPERATURE_WARNING,
                        TEMPERATURE_CRITICAL,
                        HUMIDITY_WARNING,
                        HUMIDITY_CRITICAL
                )
        );
    }

    private RuntimeConfigResponse buildDefaultRuntimeConfig(Long deviceId) {
        List<RuntimeConfigResponse.RuntimeContainer> runtimeContainers = java.util.stream.IntStream.rangeClosed(1, DEFAULT_CONTAINER_COUNT)
                .mapToObj(containerNumber -> new RuntimeConfigResponse.RuntimeContainer(containerNumber, "", "", 0, Boolean.FALSE))
                .toList();

        return new RuntimeConfigResponse(
                String.valueOf(deviceId),
                1,
                OffsetDateTime.now(UTC).toString(),
                DEVICE_TIMEZONE.getId(),
                runtimeContainers,
                List.of(),
                new RuntimeConfigResponse.EnvironmentThresholds(
                        TEMPERATURE_WARNING,
                        TEMPERATURE_CRITICAL,
                        HUMIDITY_WARNING,
                        HUMIDITY_CRITICAL
                )
        );
    }

    @Transactional
    public EnvironmentReadingResponse ingestEnvironmentReading(Long deviceId, String deviceKey, String serviceKey, EnvironmentReadingRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        Optional<EnvironmentReading> existing = environmentReadingRepository.findByDeviceIdAndEventId(deviceId, request.eventId());
        if (existing.isPresent()) {
            return toEnvironmentResponse(existing.get());
        }

        EnvironmentReading reading = new EnvironmentReading();
        reading.setDevice(device);
        reading.setEventId(request.eventId());
        reading.setTemperature(request.temperature());
        reading.setHumidity(request.humidity());
        reading.setRecordedAt(toUtc(request.recordedAt()));
        reading.setFirmwareVersion(request.firmwareVersion());
        reading.setRiskStatus(calculateRisk(request.temperature(), request.humidity()));

        EnvironmentReading saved = environmentReadingRepository.save(reading);
        return toEnvironmentResponse(saved);
    }

    @Transactional
    public HeartbeatResponse ingestHeartbeat(Long deviceId, String deviceKey, String serviceKey, HeartbeatRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        Optional<DeviceHeartbeat> existing = heartbeatRepository.findByDeviceIdAndEventId(deviceId, request.eventId());
        if (existing.isPresent()) {
            return toHeartbeatResponse(existing.get());
        }

        DeviceHeartbeat heartbeat = new DeviceHeartbeat();
        heartbeat.setDevice(device);
        heartbeat.setEventId(request.eventId());
        heartbeat.setRtcTime(toUtc(request.rtcTime()));
        heartbeat.setWifiConnected(request.wifiConnected());
        heartbeat.setMqttConnected(request.mqttConnected());
        heartbeat.setRtcOk(request.rtcOk());
        heartbeat.setSht3xOk(request.sht3xOk());
        heartbeat.setDfPlayerOk(request.dfPlayerOk());
        heartbeat.setSdCardOk(request.sdCardOk());
        heartbeat.setSwitchOk(request.switchOk());
        heartbeat.setButtonPin(request.buttonPin());
        heartbeat.setFreeHeap(request.freeHeap());
        heartbeat.setRssi(request.rssi());
        heartbeat.setDeviceStatus(request.deviceStatus().trim());
        heartbeat.setFirmwareVersion(request.firmwareVersion());
        heartbeat.setRecordedAt(OffsetDateTime.now(UTC));

        DeviceHeartbeat saved = heartbeatRepository.save(heartbeat);

        device.setLastSeenAt(saved.getRecordedAt());
        device.setLastKnownRtcTime(saved.getRtcTime());
        device.setLastKnownStatus(saved.getDeviceStatus());
        device.setLastKnownWifiConnected(saved.getWifiConnected());
        device.setLastKnownMqttConnected(saved.getMqttConnected());
        device.setLastKnownRtcOk(saved.getRtcOk());
        device.setLastKnownSht3xOk(saved.getSht3xOk());
        device.setLastKnownDfPlayerOk(saved.getDfPlayerOk());
        device.setLastKnownSdCardOk(saved.getSdCardOk());
        device.setLastKnownSwitchOk(saved.getSwitchOk());
        device.setLastKnownButtonPin(saved.getButtonPin());
        device.setLastKnownFreeHeap(saved.getFreeHeap());
        device.setLastKnownRssi(saved.getRssi());
        device.setLastKnownFirmwareVersion(saved.getFirmwareVersion());
        deviceRepository.save(device);

        return toHeartbeatResponse(saved);
    }

    @Transactional
    public IntakeEventResponse ingestIntakeEvent(Long deviceId, String deviceKey, String serviceKey, IntakeEventRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        Optional<IntakeRecord> existing = intakeRecordRepository.findByDeviceIdAndEventId(deviceId, request.eventId());
        if (existing.isPresent()) {
            return toIntakeResponse(existing.get());
        }

        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(request.scheduleId(), deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for this device"));

        if (!schedule.getContainer().getContainerNumber().equals(request.containerNumber())) {
            throw new IllegalArgumentException("containerNumber does not match schedule");
        }

        IntakeRecord record = new IntakeRecord();
        record.setDevice(device);
        record.setEventId(request.eventId());
        record.setScheduleId(request.scheduleId());
        record.setContainerNumber(request.containerNumber());
        record.setScheduledAt(toUtc(request.scheduledAt()));
        record.setConfirmedAt(request.confirmedAt() == null ? null : toUtc(request.confirmedAt()));
        record.setStatus(request.status());
        record.setSource(request.source());
        record.setButtonPin(request.buttonPin());

        IntakeRecord saved = intakeRecordRepository.save(record);
        return toIntakeResponse(saved);
    }

    @Transactional
    public StockEventResponse ingestStockEvent(Long deviceId, String deviceKey, String serviceKey, StockEventRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        Optional<DeviceStockEvent> existing = stockEventRepository.findByDeviceIdAndEventId(deviceId, request.eventId());
        if (existing.isPresent()) {
            return toStockResponse(existing.get());
        }

        if (request.containerNumber() < 1 || request.containerNumber() > DEFAULT_CONTAINER_COUNT) {
            throw new IllegalArgumentException("containerNumber must be between 1 and 5");
        }
        if (request.remainingPills() < 0) {
            throw new IllegalArgumentException("remainingPills cannot be negative");
        }

        MedicationContainer container = containerRepository.findByDeviceIdAndContainerNumber(deviceId, request.containerNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));

        container.setRemainingPills(request.remainingPills());
        containerRepository.save(container);

        DeviceStockEvent stockEvent = new DeviceStockEvent();
        stockEvent.setDevice(device);
        stockEvent.setEventId(request.eventId());
        stockEvent.setContainerNumber(request.containerNumber());
        stockEvent.setRemainingPills(request.remainingPills());
        stockEvent.setReportedAt(toUtc(request.reportedAt()));
        stockEvent.setReason(request.reason());

        DeviceStockEvent saved = stockEventRepository.save(stockEvent);
        return toStockResponse(saved);
    }

    private RuntimeConfigResponse.RuntimeContainer toRuntimeContainer(MedicationContainer container, int containerNumber) {
        if (container == null) {
            return new RuntimeConfigResponse.RuntimeContainer(containerNumber, "", "", 0, Boolean.FALSE);
        }
        return new RuntimeConfigResponse.RuntimeContainer(
                container.getContainerNumber(),
                defaultString(container.getMedicationName()),
                defaultString(container.getDosageLabel()),
                safeInteger(container.getRemainingPills(), 0),
                Boolean.TRUE.equals(container.getIsEnabled())
        );
    }

    private RuntimeConfigResponse.RuntimeSchedule toRuntimeSchedule(MedicationSchedule schedule) {
        return new RuntimeConfigResponse.RuntimeSchedule(
                String.valueOf(schedule.getId()),
                schedule.getContainer().getContainerNumber(),
                schedule.getTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                schedule.getDaysOfWeek().stream()
                        .sorted(Comparator.comparingInt(java.time.DayOfWeek::getValue))
                        .map(this::toShortDayCode)
                        .toList(),
                schedule.getContainer().getContainerNumber(),
                CONFIRMATION_WINDOW_SECONDS
        );
    }

    private EnvironmentRiskStatus calculateRisk(Double temperature, Double humidity) {
        if (temperature >= TEMPERATURE_CRITICAL || humidity >= HUMIDITY_CRITICAL) {
            return EnvironmentRiskStatus.CRITICAL;
        }
        if (temperature >= TEMPERATURE_WARNING || humidity >= HUMIDITY_WARNING) {
            return EnvironmentRiskStatus.WARNING;
        }
        return EnvironmentRiskStatus.NORMAL;
    }

    private EnvironmentReadingResponse toEnvironmentResponse(EnvironmentReading reading) {
        return new EnvironmentReadingResponse(
                reading.getEventId(),
                String.valueOf(reading.getDevice().getId()),
                reading.getTemperature(),
                reading.getHumidity(),
                reading.getRecordedAt().toString(),
                reading.getRiskStatus(),
                reading.getFirmwareVersion()
        );
    }

    private HeartbeatResponse toHeartbeatResponse(DeviceHeartbeat heartbeat) {
        return new HeartbeatResponse(
                heartbeat.getEventId(),
                String.valueOf(heartbeat.getDevice().getId()),
                heartbeat.getDeviceStatus(),
                heartbeat.getRecordedAt().toString()
        );
    }

    private IntakeEventResponse toIntakeResponse(IntakeRecord record) {
        return new IntakeEventResponse(
                record.getEventId(),
                String.valueOf(record.getDevice().getId()),
                String.valueOf(record.getScheduleId()),
                record.getContainerNumber(),
                record.getScheduledAt().toString(),
                record.getConfirmedAt() == null ? null : record.getConfirmedAt().toString(),
                record.getStatus(),
                record.getSource(),
                record.getButtonPin()
        );
    }

    private StockEventResponse toStockResponse(DeviceStockEvent stockEvent) {
        return new StockEventResponse(
                stockEvent.getEventId(),
                String.valueOf(stockEvent.getDevice().getId()),
                stockEvent.getContainerNumber(),
                stockEvent.getRemainingPills(),
                stockEvent.getReportedAt().toString(),
                stockEvent.getReason()
        );
    }

    private String toShortDayCode(java.time.DayOfWeek dayOfWeek) {
        return dayOfWeek.name().substring(0, 3);
    }

    private OffsetDateTime toUtc(LocalDateTime localDateTime) {
        return localDateTime.atZone(DEVICE_TIMEZONE).withZoneSameInstant(UTC).toOffsetDateTime();
    }

    private Integer safeInteger(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private Device authorize(Long deviceId, String deviceKey, String serviceKey) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        boolean hasServiceKey = serviceKey != null && !serviceKey.isBlank();
        boolean hasDeviceKey = deviceKey != null && !deviceKey.isBlank();

        if (!hasServiceKey && !hasDeviceKey) {
            throw new UnauthorizedException("Missing X-Edge-Service-Key or X-Device-Key");
        }

        if (hasServiceKey) {
            if (!edgeServiceKey.equals(serviceKey)) {
                throw new ForbiddenException("Invalid edge service key");
            }
            return device;
        }

        if (!device.getDeviceKey().equals(deviceKey)) {
            throw new ForbiddenException("Invalid device key");
        }
        return device;
    }
}
