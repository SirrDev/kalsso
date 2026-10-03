package com.footbooking.field;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "field")
public class Field {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldSurface surface;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 60;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public void setVenueId(UUID venueId) { this.venueId = venueId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FieldFormat getFormat() { return format; }
    public void setFormat(FieldFormat format) { this.format = format; }
    public FieldSurface getSurface() { return surface; }
    public void setSurface(FieldSurface surface) { this.surface = surface; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
