package com.dosys.platform.medication.interfaces.rest.dto.response;

import java.time.OffsetDateTime;

public record DeviceStatusResponse(
        String deviceId,
        String status,
        OffsetDateTime lastSeenAt,
        Boolean rtcOk,
        Boolean sht3xOk,
        Boolean dfPlayerOk,
        Boolean sdCardOk,
        Boolean switchOk,
        Integer buttonPin,
        Integer rssi,
        String firmwareVersion,
        String hardwareVersion,
        Boolean wifiConnected,
        Boolean mqttConnected
) {
}
