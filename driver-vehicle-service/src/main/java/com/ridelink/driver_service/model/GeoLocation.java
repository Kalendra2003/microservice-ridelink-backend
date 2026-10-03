package com.ridelink.driver_service.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Simulated current location (plain latitude/longitude, no live map integration). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocation {

    private double latitude;
    private double longitude;
    private Instant updatedAt;
}