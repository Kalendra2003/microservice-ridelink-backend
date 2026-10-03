package com.ridelink.driver_service.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.ridelink.driver_service.dto.DriverProfileRequest;
import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.EligibleDriverCriteria;
import com.ridelink.driver_service.dto.EligibleDriverResponse;
import com.ridelink.driver_service.exception.BadRequestException;
import com.ridelink.driver_service.exception.DuplicateResourceException;
import com.ridelink.driver_service.exception.InvalidStateException;
import com.ridelink.driver_service.exception.ResourceNotFoundException;
import com.ridelink.driver_service.mapper.DriverMapper;
import com.ridelink.driver_service.model.AvailabilityStatus;
import com.ridelink.driver_service.model.DriverProfile;
import com.ridelink.driver_service.model.GeoLocation;
import com.ridelink.driver_service.repository.DriverRepository;
import com.ridelink.driver_service.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    static final int MAX_LIMIT = 50;

    private final DriverRepository driverRepository;
    private final Clock clock;

    @Override
    public DriverResponse createProfile(String accountId, DriverProfileRequest request) {
        if (driverRepository.findByAccountId(accountId).isPresent()) {
            throw new DuplicateResourceException("A driver profile already exists for this account");
        }
        String license = normalise(request.licenseNumber());
        String plate = normalise(request.vehicle().plateNumber());
        assertLicenseFree(license, null);
        assertPlateFree(plate, null);

        Instant now = clock.instant();
        DriverProfile driver = DriverProfile.builder()
                .accountId(accountId)
                .fullName(request.fullName().trim())
                .phoneNumber(request.phoneNumber().trim())
                .licenseNumber(license)
                .serviceArea(request.serviceArea().trim())
                .vehicle(DriverMapper.toVehicle(request.vehicle(), plate))
                .availabilityStatus(AvailabilityStatus.OFFLINE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return DriverMapper.toResponse(driverRepository.save(driver));
    }

    @Override
    public DriverResponse getMyProfile(String accountId) {
        return DriverMapper.toResponse(findByAccount(accountId));
    }

    @Override
    public DriverResponse getById(String driverId) {
        return DriverMapper.toResponse(findById(driverId));
    }

    @Override
    public DriverResponse updateProfile(String accountId, DriverProfileRequest request) {
        DriverProfile driver = findByAccount(accountId);
        String license = normalise(request.licenseNumber());
        String plate = normalise(request.vehicle().plateNumber());
        assertLicenseFree(license, driver.getId());
        assertPlateFree(plate, driver.getId());

        driver.setFullName(request.fullName().trim());
        driver.setPhoneNumber(request.phoneNumber().trim());
        driver.setLicenseNumber(license);
        driver.setServiceArea(request.serviceArea().trim());
        driver.setVehicle(DriverMapper.toVehicle(request.vehicle(), plate));
        return save(driver);
    }

    @Override
    public DriverResponse updateServiceArea(String accountId, String serviceArea) {
        DriverProfile driver = findByAccount(accountId);
        driver.setServiceArea(serviceArea.trim());
        return save(driver);
    }

    @Override
    public DriverResponse updateAvailability(String accountId, AvailabilityStatus status) {
        DriverProfile driver = findByAccount(accountId);
        if (status == AvailabilityStatus.BUSY) {
            throw new BadRequestException("BUSY is set automatically when a ride is assigned and cannot be requested");
        }
        if (driver.getAvailabilityStatus() == AvailabilityStatus.BUSY) {
            throw new InvalidStateException("Driver is on an active ride; availability cannot be changed until it ends");
        }
        if (status == AvailabilityStatus.AVAILABLE && driver.getCurrentLocation() == null) {
            throw new InvalidStateException("Set the current location before going AVAILABLE");
        }
        driver.setAvailabilityStatus(status);
        return save(driver);
    }

    @Override
    public DriverResponse updateLocation(String accountId, double latitude, double longitude) {
        DriverProfile driver = findByAccount(accountId);
        driver.setCurrentLocation(new GeoLocation(latitude, longitude, clock.instant()));
        return save(driver);
    }

    @Override
    public List<DriverResponse> listAll(AvailabilityStatus statusFilter) {
        List<DriverProfile> drivers = statusFilter == null
                ? driverRepository.findAll()
                : driverRepository.findByAvailabilityStatus(statusFilter);
        return drivers.stream().map(DriverMapper::toResponse).toList();
    }

    @Override
    public List<EligibleDriverResponse> findEligibleDrivers(EligibleDriverCriteria criteria) {
        validate(criteria);
        boolean byDistance = criteria.latitude() != null;

        Comparator<EligibleDriverResponse> order;
        if (byDistance) {
            order = Comparator.comparingDouble(EligibleDriverResponse::distanceKm);
        } else {
            order = Comparator.comparing(r -> r.driver().createdAt());
        }

        return driverRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE).stream()
                .filter(d -> matchesServiceArea(d, criteria.serviceArea()))
                .filter(d -> criteria.vehicleType() == null
                        || d.getVehicle().getVehicleType() == criteria.vehicleType())
                .filter(d -> !byDistance || d.getCurrentLocation() != null)
                .map(d -> toEligible(d, criteria, byDistance))
                .sorted(order)
                .limit(criteria.limit())
                .toList();
    }

    @Override
    public DriverResponse reserveDriver(String driverId) {
        DriverProfile driver = findById(driverId);
        if (driver.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new InvalidStateException(
                    "Driver cannot be reserved because the status is " + driver.getAvailabilityStatus());
        }
        driver.setAvailabilityStatus(AvailabilityStatus.BUSY);
        return save(driver);
    }

    @Override
    public DriverResponse releaseDriver(String driverId) {
        DriverProfile driver = findById(driverId);
        if (driver.getAvailabilityStatus() != AvailabilityStatus.BUSY) {
            throw new InvalidStateException(
                    "Driver cannot be released because the status is " + driver.getAvailabilityStatus());
        }
        driver.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        return save(driver);
    }

    // ---------------------------------------------------------------- helpers

    private DriverProfile findByAccount(String accountId) {
        return driverRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No driver profile exists for this account"));
    }

    private DriverProfile findById(String driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));
    }

    private DriverResponse save(DriverProfile driver) {
        driver.setUpdatedAt(clock.instant());
        return DriverMapper.toResponse(driverRepository.save(driver));
    }

    private void assertLicenseFree(String license, String ownId) {
        driverRepository.findByLicenseNumber(license)
                .filter(other -> !Objects.equals(other.getId(), ownId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("License number is already registered");
                });
    }

    private void assertPlateFree(String plate, String ownId) {
        driverRepository.findByVehiclePlateNumber(plate)
                .filter(other -> !Objects.equals(other.getId(), ownId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Vehicle plate number is already registered");
                });
    }

    private static String normalise(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static void validate(EligibleDriverCriteria criteria) {
        if (criteria.limit() < 1 || criteria.limit() > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if ((criteria.latitude() == null) != (criteria.longitude() == null)) {
            throw new BadRequestException("latitude and longitude must be provided together");
        }
        if (criteria.latitude() != null
                && (criteria.latitude() < -90 || criteria.latitude() > 90
                || criteria.longitude() < -180 || criteria.longitude() > 180)) {
            throw new BadRequestException("latitude must be within [-90, 90] and longitude within [-180, 180]");
        }
    }

    private static boolean matchesServiceArea(DriverProfile driver, String requestedArea) {
        return requestedArea == null
                || requestedArea.isBlank()
                || requestedArea.trim().equalsIgnoreCase(driver.getServiceArea());
    }

    private static EligibleDriverResponse toEligible(DriverProfile driver, EligibleDriverCriteria criteria,
                                                     boolean byDistance) {
        Double distance = null;
        if (byDistance) {
            double km = DistanceCalculator.kilometres(
                    criteria.latitude(), criteria.longitude(),
                    driver.getCurrentLocation().getLatitude(), driver.getCurrentLocation().getLongitude());
            distance = Math.round(km * 100.0) / 100.0;
        }
        return new EligibleDriverResponse(DriverMapper.toResponse(driver), distance);
    }
}