package com.dosys.platform.device.interfaces.rest.internal;

import com.dosys.platform.device.application.DeviceInternalService;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.EnvironmentReadingRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.HeartbeatRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.IntakeEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.request.StockEventRequest;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.AcknowledgementResponse;
import com.dosys.platform.device.interfaces.rest.internal.dto.response.RuntimeConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device/internal")
@Tag(name = "Device Internal")
public class DeviceInternalController {

    private final DeviceInternalService deviceInternalService;

    public DeviceInternalController(DeviceInternalService deviceInternalService) {
        this.deviceInternalService = deviceInternalService;
    }

    @GetMapping("/{deviceId}/runtime-config")
    @Operation(summary = "Get internal runtime config for edge integration")
    public RuntimeConfigResponse getRuntimeConfig(
            @PathVariable Long deviceId,
            @Parameter(description = "Internal technical key") @RequestHeader(name = "X-Device-Key", required = false) String deviceKey) {
        return deviceInternalService.getRuntimeConfig(deviceId, deviceKey);
    }

    @PostMapping("/{deviceId}/intake-events")
    @Operation(summary = "Ingest intake event from edge integration")
    public AcknowledgementResponse ingestIntakeEvent(
            @PathVariable Long deviceId,
            @RequestHeader(name = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody IntakeEventRequest request) {
        return deviceInternalService.ingestIntakeEvent(deviceId, deviceKey, request);
    }

    @PostMapping("/{deviceId}/environment-readings")
    @Operation(summary = "Ingest environment readings from edge integration")
    public AcknowledgementResponse ingestEnvironmentReading(
            @PathVariable Long deviceId,
            @RequestHeader(name = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody EnvironmentReadingRequest request) {
        return deviceInternalService.ingestEnvironmentReading(deviceId, deviceKey, request);
    }

    @PostMapping("/{deviceId}/stock-events")
    @Operation(summary = "Ingest stock updates from edge integration")
    public AcknowledgementResponse ingestStockEvent(
            @PathVariable Long deviceId,
            @RequestHeader(name = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody StockEventRequest request) {
        return deviceInternalService.ingestStockEvent(deviceId, deviceKey, request);
    }

    @PostMapping("/{deviceId}/heartbeats")
    @Operation(summary = "Ingest heartbeat from edge integration")
    public AcknowledgementResponse ingestHeartbeat(
            @PathVariable Long deviceId,
            @RequestHeader(name = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody HeartbeatRequest request) {
        return deviceInternalService.ingestHeartbeat(deviceId, deviceKey, request);
    }
}
