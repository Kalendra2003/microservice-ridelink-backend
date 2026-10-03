package com.ridelink.driver_service.dto;

import com.ridelink.driver_service.model.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(min = 3, max = 15)
        @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "plateNumber may contain only letters, digits, spaces and hyphens")
        String plateNumber,
        @NotBlank @Size(max = 50) String make,
        @NotBlank @Size(max = 50) String model,
        @NotBlank @Size(max = 30) String color,
        @NotNull VehicleType vehicleType,
        @NotNull @Min(1) @Max(12) Integer seatCapacity,
        @NotNull @Min(1990) @Max(2100) Integer manufactureYear) {
}