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

@Entity
@Table(name = "medication_containers")
public class MedicationContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "container_number", nullable = false)
    private Integer containerNumber;

    @Column(name = "medication_name", length = 150)
    private String medicationName;

    @Column(name = "dosage_label", length = 150)
    private String dosageLabel;

    @Column(name = "remaining_pills", nullable = false)
    private Integer remainingPills;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public Integer getContainerNumber() { return containerNumber; }
    public void setContainerNumber(Integer containerNumber) { this.containerNumber = containerNumber; }
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }
    public String getDosageLabel() { return dosageLabel; }
    public void setDosageLabel(String dosageLabel) { this.dosageLabel = dosageLabel; }
    public Integer getRemainingPills() { return remainingPills; }
    public void setRemainingPills(Integer remainingPills) { this.remainingPills = remainingPills; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean enabled) { isEnabled = enabled; }
}
