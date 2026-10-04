package com.footbooking.field;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "pricing_rule")
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    // 1 = lundi ... 7 = dimanche ; null = tous les jours
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // La colonne SQL est CHAR(3) : sans cette annotation, Hibernate attend un VARCHAR
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    public UUID getId() { return id; }
    public UUID getFieldId() { return fieldId; }
    public void setFieldId(UUID fieldId) { this.fieldId = fieldId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}