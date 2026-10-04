package com.footbooking.search;

import com.footbooking.field.FieldFormat;
import com.footbooking.field.FieldSurface;

import java.util.UUID;

public record FieldSummary(UUID fieldId, String fieldName, FieldFormat format, FieldSurface surface,
                           Integer slotMinutes, UUID venueId, String venueName, String city, String address) {}