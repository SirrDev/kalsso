package com.footbooking.field;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "closed_day")
public class ClosedDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    @Column(nullable = false)
    private LocalDate day;

    private String reason;

    public UUID getId() { return id; }
    public UUID getFieldId() { return fieldId; }
    public void setFieldId(UUID fieldId) { this.fieldId = fieldId; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}