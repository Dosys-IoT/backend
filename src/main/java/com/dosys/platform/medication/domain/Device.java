package com.dosys.platform.medication.domain;

import com.dosys.platform.access.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @Column(name = "humidity_threshold", nullable = false)
    private Double humidityThreshold;

    @Column(name = "temperature_threshold", nullable = false)
    private Double temperatureThreshold;

    @Column(name = "device_key", nullable = false, unique = true, length = 120)
    private String deviceKey;

    @Column(name = "hardware_device_id", unique = true)
    private Long hardwareDeviceId;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_known_rtc_time")
    private OffsetDateTime lastKnownRtcTime;

    @Column(name = "last_known_status", length = 60)
    private String lastKnownStatus;

    @Column(name = "last_known_wifi_connected")
    private Boolean lastKnownWifiConnected;

    @Column(name = "last_known_mqtt_connected")
    private Boolean lastKnownMqttConnected;

    @Column(name = "last_known_rtc_ok")
    private Boolean lastKnownRtcOk;

    @Column(name = "last_known_sht3x_ok")
    private Boolean lastKnownSht3xOk;

    @Column(name = "last_known_dfplayer_ok")
    private Boolean lastKnownDfPlayerOk;

    @Column(name = "last_known_sd_card_ok")
    private Boolean lastKnownSdCardOk;

    @Column(name = "last_known_switch_ok")
    private Boolean lastKnownSwitchOk;

    @Column(name = "last_known_button_pin")
    private Integer lastKnownButtonPin;

    @Column(name = "last_known_free_heap")
    private Long lastKnownFreeHeap;

    @Column(name = "last_known_rssi")
    private Integer lastKnownRssi;

    @Column(name = "last_known_firmware_version", length = 60)
    private String lastKnownFirmwareVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public Double getHumidityThreshold() { return humidityThreshold; }
    public void setHumidityThreshold(Double humidityThreshold) { this.humidityThreshold = humidityThreshold; }
    public Double getTemperatureThreshold() { return temperatureThreshold; }
    public void setTemperatureThreshold(Double temperatureThreshold) { this.temperatureThreshold = temperatureThreshold; }
    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }
    public Long getHardwareDeviceId() { return hardwareDeviceId; }
    public void setHardwareDeviceId(Long hardwareDeviceId) { this.hardwareDeviceId = hardwareDeviceId; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public OffsetDateTime getLastKnownRtcTime() { return lastKnownRtcTime; }
    public void setLastKnownRtcTime(OffsetDateTime lastKnownRtcTime) { this.lastKnownRtcTime = lastKnownRtcTime; }
    public String getLastKnownStatus() { return lastKnownStatus; }
    public void setLastKnownStatus(String lastKnownStatus) { this.lastKnownStatus = lastKnownStatus; }
    public Boolean getLastKnownWifiConnected() { return lastKnownWifiConnected; }
    public void setLastKnownWifiConnected(Boolean lastKnownWifiConnected) { this.lastKnownWifiConnected = lastKnownWifiConnected; }
    public Boolean getLastKnownMqttConnected() { return lastKnownMqttConnected; }
    public void setLastKnownMqttConnected(Boolean lastKnownMqttConnected) { this.lastKnownMqttConnected = lastKnownMqttConnected; }
    public Boolean getLastKnownRtcOk() { return lastKnownRtcOk; }
    public void setLastKnownRtcOk(Boolean lastKnownRtcOk) { this.lastKnownRtcOk = lastKnownRtcOk; }
    public Boolean getLastKnownSht3xOk() { return lastKnownSht3xOk; }
    public void setLastKnownSht3xOk(Boolean lastKnownSht3xOk) { this.lastKnownSht3xOk = lastKnownSht3xOk; }
    public Boolean getLastKnownDfPlayerOk() { return lastKnownDfPlayerOk; }
    public void setLastKnownDfPlayerOk(Boolean lastKnownDfPlayerOk) { this.lastKnownDfPlayerOk = lastKnownDfPlayerOk; }
    public Boolean getLastKnownSdCardOk() { return lastKnownSdCardOk; }
    public void setLastKnownSdCardOk(Boolean lastKnownSdCardOk) { this.lastKnownSdCardOk = lastKnownSdCardOk; }
    public Boolean getLastKnownSwitchOk() { return lastKnownSwitchOk; }
    public void setLastKnownSwitchOk(Boolean lastKnownSwitchOk) { this.lastKnownSwitchOk = lastKnownSwitchOk; }
    public Integer getLastKnownButtonPin() { return lastKnownButtonPin; }
    public void setLastKnownButtonPin(Integer lastKnownButtonPin) { this.lastKnownButtonPin = lastKnownButtonPin; }
    public Long getLastKnownFreeHeap() { return lastKnownFreeHeap; }
    public void setLastKnownFreeHeap(Long lastKnownFreeHeap) { this.lastKnownFreeHeap = lastKnownFreeHeap; }
    public Integer getLastKnownRssi() { return lastKnownRssi; }
    public void setLastKnownRssi(Integer lastKnownRssi) { this.lastKnownRssi = lastKnownRssi; }
    public String getLastKnownFirmwareVersion() { return lastKnownFirmwareVersion; }
    public void setLastKnownFirmwareVersion(String lastKnownFirmwareVersion) { this.lastKnownFirmwareVersion = lastKnownFirmwareVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
