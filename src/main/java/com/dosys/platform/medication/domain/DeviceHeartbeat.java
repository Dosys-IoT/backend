package com.dosys.platform.medication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_heartbeats")
public class DeviceHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "event_id", nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(name = "rtc_time")
    private OffsetDateTime rtcTime;

    @Column(name = "wifi_connected")
    private Boolean wifiConnected;

    @Column(name = "mqtt_connected")
    private Boolean mqttConnected;

    @Column(name = "rtc_ok")
    private Boolean rtcOk;

    @Column(name = "sht3x_ok")
    private Boolean sht3xOk;

    @Column(name = "dfplayer_ok")
    private Boolean dfPlayerOk;

    @Column(name = "sd_card_ok")
    private Boolean sdCardOk;

    @Column(name = "switch_ok")
    private Boolean switchOk;

    @Column(name = "button_pin")
    private Integer buttonPin;

    @Column(name = "free_heap")
    private Long freeHeap;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "device_status", length = 40)
    private String deviceStatus;

    @Column(name = "firmware_version", length = 60)
    private String firmwareVersion;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public OffsetDateTime getRtcTime() { return rtcTime; }
    public void setRtcTime(OffsetDateTime rtcTime) { this.rtcTime = rtcTime; }
    public Boolean getWifiConnected() { return wifiConnected; }
    public void setWifiConnected(Boolean wifiConnected) { this.wifiConnected = wifiConnected; }
    public Boolean getMqttConnected() { return mqttConnected; }
    public void setMqttConnected(Boolean mqttConnected) { this.mqttConnected = mqttConnected; }
    public Boolean getRtcOk() { return rtcOk; }
    public void setRtcOk(Boolean rtcOk) { this.rtcOk = rtcOk; }
    public Boolean getSht3xOk() { return sht3xOk; }
    public void setSht3xOk(Boolean sht3xOk) { this.sht3xOk = sht3xOk; }
    public Boolean getDfPlayerOk() { return dfPlayerOk; }
    public void setDfPlayerOk(Boolean dfPlayerOk) { this.dfPlayerOk = dfPlayerOk; }
    public Boolean getSdCardOk() { return sdCardOk; }
    public void setSdCardOk(Boolean sdCardOk) { this.sdCardOk = sdCardOk; }
    public Boolean getSwitchOk() { return switchOk; }
    public void setSwitchOk(Boolean switchOk) { this.switchOk = switchOk; }
    public Integer getButtonPin() { return buttonPin; }
    public void setButtonPin(Integer buttonPin) { this.buttonPin = buttonPin; }
    public Long getFreeHeap() { return freeHeap; }
    public void setFreeHeap(Long freeHeap) { this.freeHeap = freeHeap; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
    public String getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(String deviceStatus) { this.deviceStatus = deviceStatus; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
