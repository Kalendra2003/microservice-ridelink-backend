package com.ridelink.driver_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Used for both creating and fully updating a driver profile. */
public record DriverProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "phoneNumber must contain 9-15 digits with an optional leading +")
        String phoneNumber,
        @NotBlank @Size(min = 5, max = 20) String licenseNumber,
        @NotBlank @Size(max = 100) String serviceArea,
        @NotNull @Valid VehicleRequest vehicle) {
}