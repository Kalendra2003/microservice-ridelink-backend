package com.ridelink.driver_service.exception;

import java.time.Instant;
import java.util.Map;

/** The single error body shape returned by every endpoint. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}