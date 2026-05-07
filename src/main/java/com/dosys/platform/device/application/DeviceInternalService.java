package com.dosys.platform.device.application;

import com.dosys.platform.device.interfaces.rest.internal.dto.request.EnvironmentReadingRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.HeartbeatRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.IntakeEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.StockEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.AcknowledgementResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.RuntimeConfigResponse;
import com.dosys.platform.medication.domain.Device;
import com.dosys.platform.medication.domain.EnvironmentReading;
import com.dosys.platform.medication.domain.EnvironmentRiskStatus;
import com.dosys.platform.medication.domain.IntakeRecord;
import com.dosys.platform.medication.domain.MedicationContainer;
import com.dosys.platform.medication.domain.MedicationSchedule;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DeviceInternalService {

    private final DeviceRepository deviceRepository;
    private final MedicationContainerRepository containerRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final IntakeRecordRepository intakeRecordRepository;
    private final EnvironmentReadingRepository environmentReadingRepository;
    private final String edgeServiceKey;

    public DeviceInternalService(DeviceRepository deviceRepository,
                                 MedicationContainerRepository containerRepository,
                                 MedicationScheduleRepository scheduleRepository,
                                 IntakeRecordRepository intakeRecordRepository,
                                 EnvironmentReadingRepository environmentReadingRepository,
                                 @Value("${app.edge.service-key}") String edgeServiceKey) {
        this.deviceRepository = deviceRepository;
        this.containerRepository = containerRepository;
        this.scheduleRepository = scheduleRepository;
        this.intakeRecordRepository = intakeRecordRepository;
        this.environmentReadingRepository = environmentReadingRepository;
        this.edgeServiceKey = edgeServiceKey;
    }

    @Transactional(readOnly = true)
    public RuntimeConfigResponse getRuntimeConfig(Long deviceId, String deviceKey, String serviceKey) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        List<RuntimeConfigResponse.RuntimeContainer> activeContainers = containerRepository.findByDeviceIdOrderByContainerNumberAsc(deviceId)
                .stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsEnabled()))
                .map(c -> new RuntimeConfigResponse.RuntimeContainer(c.getContainerNumber(), c.getMedicationName(), c.getDosageLabel(), c.getRemainingPills(), c.getIsEnabled()))
                .toList();

        List<RuntimeConfigResponse.RuntimeSchedule> activeSchedules = scheduleRepository.findByDeviceIdOrderByTimeAsc(deviceId)
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(s -> new RuntimeConfigResponse.RuntimeSchedule(s.getId(), s.getContainer().getContainerNumber(), s.getTime(), s.getDaysOfWeek()))
                .toList();

        return new RuntimeConfigResponse(
                device.getId(),
                device.getConfigVersion(),
                OffsetDateTime.now(ZoneId.of("America/Lima")),
                "America/Lima",
                device.getHumidityThreshold(),
                device.getTemperatureThreshold(),
                activeContainers,
                activeSchedules
        );
    }

    @Transactional
    public AcknowledgementResponse ingestIntakeEvent(Long deviceId, String deviceKey, String serviceKey, IntakeEventRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        MedicationSchedule schedule = scheduleRepository.findByIdAndDeviceId(request.scheduleId(), deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for this device"));

        if (!schedule.getContainer().getContainerNumber().equals(request.containerNumber())) {
            throw new IllegalArgumentException("containerNumber does not match schedule");
        }

        IntakeRecord record = intakeRecordRepository
                .findByDeviceIdAndScheduleIdAndScheduledAt(deviceId, request.scheduleId(), request.scheduledAt())
                .orElseGet(IntakeRecord::new);

        record.setDevice(device);
        record.setScheduleId(request.scheduleId());
        record.setContainerNumber(request.containerNumber());
        record.setScheduledAt(request.scheduledAt());
        record.setConfirmedAt(request.confirmedAt());
        record.setStatus(request.status());

        intakeRecordRepository.save(record);
        return new AcknowledgementResponse("intake event processed");
    }

    @Transactional
    public AcknowledgementResponse ingestEnvironmentReading(Long deviceId, String deviceKey, String serviceKey, EnvironmentReadingRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        EnvironmentReading reading = new EnvironmentReading();
        reading.setDevice(device);
        reading.setTemperature(request.temperature());
        reading.setHumidity(request.humidity());
        reading.setRecordedAt(request.recordedAt());

        boolean overTemp = request.temperature() > device.getTemperatureThreshold();
        boolean overHumidity = request.humidity() > device.getHumidityThreshold();

        EnvironmentRiskStatus risk = EnvironmentRiskStatus.NORMAL;
        if (overTemp || overHumidity) {
            risk = EnvironmentRiskStatus.RISK;
        }

        reading.setRiskStatus(risk);
        environmentReadingRepository.save(reading);
        return new AcknowledgementResponse("environment reading stored");
    }

    @Transactional
    public AcknowledgementResponse ingestStockEvent(Long deviceId, String deviceKey, String serviceKey, StockEventRequest request) {
        authorize(deviceId, deviceKey, serviceKey);
        if (request.containerNumber() < 1 || request.containerNumber() > 5) {
            throw new IllegalArgumentException("containerNumber must be between 1 and 5");
        }
        if (request.remainingPills() < 0) {
            throw new IllegalArgumentException("remainingPills cannot be negative");
        }

        MedicationContainer container = containerRepository.findByDeviceIdAndContainerNumber(deviceId, request.containerNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));

        container.setRemainingPills(request.remainingPills());
        containerRepository.save(container);
        return new AcknowledgementResponse("stock updated");
    }

    @Transactional
    public AcknowledgementResponse ingestHeartbeat(Long deviceId, String deviceKey, String serviceKey, HeartbeatRequest request) {
        Device device = authorize(deviceId, deviceKey, serviceKey);

        device.setLastSeenAt(request.recordedAt());
        device.setLastKnownRtcTime(request.rtcTime());
        device.setLastKnownWifiConnected(request.wifiConnected());
        device.setLastKnownStatus(request.deviceStatus().trim());
        deviceRepository.save(device);

        return new AcknowledgementResponse("heartbeat recorded");
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
