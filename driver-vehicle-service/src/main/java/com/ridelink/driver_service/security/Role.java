package com.ridelink.driver_service.security;

public enum Role {
    PASSENGER,
    DRIVER,
    ADMIN,
    /** Used by other RideLink services (e.g. Ride Service) for service-to-service calls. */
    SERVICE
}