package com.ridelink.driver_service.dto;

/** A driver that can be offered a ride; distanceKm is null when no pickup coordinates were supplied. */
public record EligibleDriverResponse(DriverResponse driver, Double distanceKm) {
}