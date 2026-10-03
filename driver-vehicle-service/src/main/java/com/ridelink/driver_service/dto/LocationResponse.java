package com.ridelink.driver_service.dto;

import java.time.Instant;

public record LocationResponse(double latitude, double longitude, Instant updatedAt) {
}