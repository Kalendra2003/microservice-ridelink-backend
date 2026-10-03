package com.ridelink.driver_service.dto;

import com.ridelink.driver_service.model.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull AvailabilityStatus status) {
}