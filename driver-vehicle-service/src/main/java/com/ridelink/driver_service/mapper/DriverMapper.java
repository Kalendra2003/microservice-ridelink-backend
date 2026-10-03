package com.ridelink.driver_service.mapper;

import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.LocationResponse;
import com.ridelink.driver_service.dto.VehicleRequest;
import com.ridelink.driver_service.dto.VehicleResponse;
import com.ridelink.driver_service.model.DriverProfile;
import com.ridelink.driver_service.model.GeoLocation;
import com.ridelink.driver_service.model.Vehicle;

public final class DriverMapper {

    private DriverMapper() {
    }

    public static DriverResponse toResponse(DriverProfile driver) {
        Vehicle vehicle = driver.getVehicle();
        VehicleResponse vehicleResponse = new VehicleResponse(
                vehicle.getPlateNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getVehicleType(),
                vehicle.getSeatCapacity(),
                vehicle.getManufactureYear());

        GeoLocation location = driver.getCurrentLocation();
        LocationResponse locationResponse = location == null
                ? null
                : new LocationResponse(location.getLatitude(), location.getLongitude(), location.getUpdatedAt());

        return new DriverResponse(
                driver.getId(),
                driver.getAccountId(),
                driver.getFullName(),
                driver.getPhoneNumber(),
                driver.getLicenseNumber(),
                driver.getServiceArea(),
                driver.getAvailabilityStatus(),
                vehicleResponse,
                locationResponse,
                driver.getCreatedAt(),
                driver.getUpdatedAt());
    }

    /** The plate number is passed in already normalised (trimmed, upper case). */
    public static Vehicle toVehicle(VehicleRequest request, String normalisedPlate) {
        return Vehicle.builder()
                .plateNumber(normalisedPlate)
                .make(request.make().trim())
                .model(request.model().trim())
                .color(request.color().trim())
                .vehicleType(request.vehicleType())
                .seatCapacity(request.seatCapacity())
                .manufactureYear(request.manufactureYear())
                .build();
    }
}