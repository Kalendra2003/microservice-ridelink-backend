package com.ridelink.driver_service.repository;

import java.util.List;
import java.util.Optional;

import com.ridelink.driver_service.model.AvailabilityStatus;
import com.ridelink.driver_service.model.DriverProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DriverRepository extends MongoRepository<DriverProfile, String> {

    Optional<DriverProfile> findByAccountId(String accountId);

    Optional<DriverProfile> findByLicenseNumber(String licenseNumber);

    Optional<DriverProfile> findByVehiclePlateNumber(String plateNumber);

    List<DriverProfile> findByAvailabilityStatus(AvailabilityStatus status);
}