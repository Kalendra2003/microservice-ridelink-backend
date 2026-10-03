package com.ridelink.driver_service.dto;

import com.ridelink.driver_service.model.VehicleType;

public record VehicleResponse(
        String plateNumber,
        String make,
        String model,
        String color,
        VehicleType vehicleType,
        int seatCapacity,
        int manufactureYear) {
}