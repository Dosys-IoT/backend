package com.dosys.platform.medication.interfaces.rest;

import com.dosys.platform.medication.application.MedicationService;
import com.dosys.platform.medication.interfaces.rest.dto.request.CreateDeviceRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.LinkPhysicalDeviceRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpdateAlarmSettingsRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertContainerRequest;
import com.dosys.platform.medication.interfaces.rest.dto.request.UpsertScheduleRequest;
import com.dosys.platform.medication.interfaces.rest.dto.response.AdherenceCalendarResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.AlarmSettingsResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ContainerResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ContainerSchedulesResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.DeviceResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.DeviceStatusResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.EdgeCredentialsResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.EnvironmentReadingResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.LinkPhysicalDeviceResponse;
import com.dosys.platform.medication.interfaces.rest.dto.response.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/medication")
@Tag(name = "Medication")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create device for authenticated user")
    public DeviceResponse createDevice(Principal principal, @Valid @RequestBody CreateDeviceRequest request) {
        return medicationService.createDevice(principal.getName(), request);
    }

    @PostMapping("/devices/link")
    @Operation(summary = "Link a physical device to the authenticated user")
    public LinkPhysicalDeviceResponse linkPhysicalDevice(Principal principal,
                                                         @Valid @RequestBody LinkPhysicalDeviceRequest request) {
        return medicationService.linkPhysicalDevice(principal.getName(), request);
    }

    @PostMapping("/devices/{deviceId}/unlink")
    @Operation(summary = "Unlink a physical device from the authenticated user")
    public LinkPhysicalDeviceResponse unlinkPhysicalDevice(Principal principal, @PathVariable Long deviceId) {
        return medicationService.unlinkPhysicalDevice(principal.getName(), deviceId);
    }

    @GetMapping("/devices")
    @Operation(summary = "List devices for authenticated user")
    public List<DeviceResponse> getDevices(Principal principal) {
        return medicationService.getDevices(principal.getName());
    }

    @GetMapping("/devices/{deviceId}/containers")
    @Operation(summary = "List all containers for a device")
    public List<ContainerResponse> getContainers(Principal principal, @PathVariable Long deviceId) {
        return medicationService.getContainers(principal.getName(), deviceId);
    }

    @PutMapping("/devices/{deviceId}/containers/{containerNumber}")
    @Operation(summary = "Create or update container configuration")
    public ContainerResponse upsertContainer(Principal principal,
                                             @PathVariable Long deviceId,
                                             @PathVariable Integer containerNumber,
                                             @Valid @RequestBody UpsertContainerRequest request) {
        return medicationService.upsertContainer(principal.getName(), deviceId, containerNumber, request);
    }

    @GetMapping("/devices/{deviceId}/schedules")
    @Operation(summary = "List schedules for a device")
    public List<ScheduleResponse> getSchedules(Principal principal, @PathVariable Long deviceId) {
        return medicationService.getSchedules(principal.getName(), deviceId);
    }

    @GetMapping("/devices/{deviceId}/containers/{containerNumber}/schedules")
    @Operation(summary = "List active schedules for a container")
    public ContainerSchedulesResponse getContainerSchedules(Principal principal,
                                                           @PathVariable Long deviceId,
                                                           @PathVariable Integer containerNumber) {
        return medicationService.getContainerSchedules(principal.getName(), deviceId, containerNumber);
    }

    @PostMapping("/devices/{deviceId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create schedule for a container")
    public ScheduleResponse createSchedule(Principal principal,
                                           @PathVariable Long deviceId,
                                           @Valid @RequestBody UpsertScheduleRequest request) {
        return medicationService.createSchedule(principal.getName(), deviceId, request);
    }

    @PutMapping("/devices/{deviceId}/schedules/{scheduleId}")
    @Operation(summary = "Update schedule")
    public ScheduleResponse updateSchedule(Principal principal,
                                           @PathVariable Long deviceId,
                                           @PathVariable Long scheduleId,
                                           @Valid @RequestBody UpsertScheduleRequest request) {
        return medicationService.updateSchedule(principal.getName(), deviceId, scheduleId, request);
    }

    @DeleteMapping("/devices/{deviceId}/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete schedule")
    public void deleteSchedule(Principal principal,
                               @PathVariable Long deviceId,
                               @PathVariable Long scheduleId) {
        medicationService.deleteSchedule(principal.getName(), deviceId, scheduleId);
    }

    @GetMapping("/devices/{deviceId}/adherence/calendar")
    @Operation(summary = "Get monthly adherence calendar")
    public AdherenceCalendarResponse getAdherenceCalendar(Principal principal,
                                                          @PathVariable Long deviceId,
                                                          @RequestParam String month) {
        return medicationService.getAdherenceCalendar(principal.getName(), deviceId, month);
    }

    @GetMapping("/devices/{deviceId}/environment/latest")
    @Operation(summary = "Get latest environment reading")
    public EnvironmentReadingResponse getLatestEnvironment(Principal principal, @PathVariable Long deviceId) {
        return medicationService.getLatestEnvironment(principal.getName(), deviceId);
    }

    @GetMapping("/devices/{deviceId}/environment/history")
    @Operation(summary = "Get environment history in range")
    public List<EnvironmentReadingResponse> getEnvironmentHistory(Principal principal,
                                                                  @PathVariable Long deviceId,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return medicationService.getEnvironmentHistory(principal.getName(), deviceId, from, to);
    }

    @GetMapping("/devices/{deviceId}/status")
    @Operation(summary = "Get latest device status for frontend")
    public DeviceStatusResponse getDeviceStatus(Principal principal, @PathVariable Long deviceId) {
        return medicationService.getDeviceStatus(principal.getName(), deviceId);
    }

    @PutMapping("/devices/{deviceId}/alarm-settings")
    @Operation(summary = "Update device alarm settings")
    public AlarmSettingsResponse updateAlarmSettings(Principal principal,
                                                     @PathVariable Long deviceId,
                                                     @Valid @RequestBody UpdateAlarmSettingsRequest request) {
        return medicationService.updateAlarmSettings(principal.getName(), deviceId, request);
    }

    @GetMapping("/devices/{deviceId}/edge-credentials")
    @Operation(summary = "Get edge credentials for a owned device")
    public EdgeCredentialsResponse getEdgeCredentials(Principal principal, @PathVariable Long deviceId) {
        return medicationService.getEdgeCredentials(principal.getName(), deviceId);
    }
}
