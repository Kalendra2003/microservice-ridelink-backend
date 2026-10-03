package com.ridelink.driver_service.security;

/** Identity extracted from a verified JWT: userId is the JWT subject (the Account Service id). */
public record AuthenticatedUser(String userId, Role role) {
}