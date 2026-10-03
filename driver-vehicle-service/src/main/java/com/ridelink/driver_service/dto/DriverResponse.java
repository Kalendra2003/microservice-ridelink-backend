package com.ridelink.driver_service.dto;

import java.time.Instant;

import com.ridelink.driver_service.model.AvailabilityStatus;

public record DriverResponse(
        String id,
        String accountId,
        String fullName,
        String phoneNumber,
        String licenseNumber,
        String serviceArea,
        AvailabilityStatus availabilityStatus,
        VehicleResponse vehicle,
        LocationResponse currentLocation,
        Instant createdAt,
        Instant updatedAt) {
}