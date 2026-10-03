package com.ridelink.driver_service.service;

import java.util.List;

import com.ridelink.driver_service.dto.DriverProfileRequest;
import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.EligibleDriverCriteria;
import com.ridelink.driver_service.dto.EligibleDriverResponse;
import com.ridelink.driver_service.model.AvailabilityStatus;

public interface DriverService {

    DriverResponse createProfile(String accountId, DriverProfileRequest request);

    DriverResponse getMyProfile(String accountId);

    DriverResponse getById(String driverId);

    DriverResponse updateProfile(String accountId, DriverProfileRequest request);

    DriverResponse updateServiceArea(String accountId, String serviceArea);

    DriverResponse updateAvailability(String accountId, AvailabilityStatus status);

    DriverResponse updateLocation(String accountId, double latitude, double longitude);

    List<DriverResponse> listAll(AvailabilityStatus statusFilter);

    /** Available drivers matching the criteria, nearest first when pickup coordinates are supplied. */
    List<EligibleDriverResponse> findEligibleDrivers(EligibleDriverCriteria criteria);

    /** Called by the Ride Service when it assigns a driver: AVAILABLE -> BUSY. */
    DriverResponse reserveDriver(String driverId);

    /** Called by the Ride Service when a ride is completed or cancelled: BUSY -> AVAILABLE. */
    DriverResponse releaseDriver(String driverId);
}