package com.ridelink.driver_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

/** Embedded document: a driver owns exactly one registered vehicle. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Indexed(unique = true)
    private String plateNumber;
    private String make;
    private String model;
    private String color;
    private VehicleType vehicleType;
    private int seatCapacity;
    private int manufactureYear;
}