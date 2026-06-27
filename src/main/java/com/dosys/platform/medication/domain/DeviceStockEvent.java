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
@Table(name = "device_stock_events")
public class DeviceStockEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "event_id", nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(name = "container_number", nullable = false)
    private Integer containerNumber;

    @Column(name = "remaining_pills", nullable = false)
    private Integer remainingPills;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @Column(name = "reason", length = 60)
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Integer getContainerNumber() { return containerNumber; }
    public void setContainerNumber(Integer containerNumber) { this.containerNumber = containerNumber; }
    public Integer getRemainingPills() { return remainingPills; }
    public void setRemainingPills(Integer remainingPills) { this.remainingPills = remainingPills; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(OffsetDateTime reportedAt) { this.reportedAt = reportedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
