package com.ridelink.driver_service.dto;

import com.ridelink.driver_service.model.VehicleType;

/** Search criteria for eligible available drivers. Every field except limit is optional. */
public record EligibleDriverCriteria(
        String serviceArea,
        VehicleType vehicleType,
        Double latitude,
        Double longitude,
        int limit) {
}