package com.ridelink.driver_service.controller;

import java.net.URI;
import java.util.List;

import com.ridelink.driver_service.config.OpenApiConfig;
import com.ridelink.driver_service.dto.AvailabilityRequest;
import com.ridelink.driver_service.dto.DriverProfileRequest;
import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.EligibleDriverCriteria;
import com.ridelink.driver_service.dto.EligibleDriverResponse;
import com.ridelink.driver_service.dto.LocationRequest;
import com.ridelink.driver_service.dto.ServiceAreaRequest;
import com.ridelink.driver_service.model.AvailabilityStatus;
import com.ridelink.driver_service.model.VehicleType;
import com.ridelink.driver_service.security.AuthInterceptor;
import com.ridelink.driver_service.security.AuthenticatedUser;
import com.ridelink.driver_service.security.RequireRole;
import com.ridelink.driver_service.security.Role;
import com.ridelink.driver_service.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Driver & vehicle operations")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class DriverController {

    private final DriverService driverService;

    // ------------------------------------------------ driver self-service

    @PostMapping
    @RequireRole(Role.DRIVER)
    @Operation(summary = "Create my driver profile and register my vehicle (starts OFFLINE)")
    public ResponseEntity<DriverResponse> createProfile(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody DriverProfileRequest request) {
        DriverResponse created = driverService.createProfile(user.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + created.id())).body(created);
    }

    @GetMapping("/me")
    @RequireRole(Role.DRIVER)
    @Operation(summary = "View my driver profile")
    public DriverResponse getMyProfile(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user) {
        return driverService.getMyProfile(user.userId());
    }

    @PutMapping("/me")
    @RequireRole(Role.DRIVER)
    @Operation(summary = "Update my profile and vehicle details")
    public DriverResponse updateMyProfile(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody DriverProfileRequest request) {
        return driverService.updateProfile(user.userId(), request);
    }

    @PatchMapping("/me/service-area")
    @RequireRole(Role.DRIVER)
    @Operation(summary = "Change my service area")
    public DriverResponse updateServiceArea(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ServiceAreaRequest request) {
        return driverService.updateServiceArea(user.userId(), request.serviceArea());
    }

    @PatchMapping("/me/availability")
    @RequireRole(Role.DRIVER)
    @Operation(summary = "Go AVAILABLE or OFFLINE (BUSY is set automatically by ride assignment)")
    public DriverResponse updateAvailability(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody AvailabilityRequest request) {
        return driverService.updateAvailability(user.userId(), request.status());
    }

    @PutMapping("/me/location")
    @RequireRole(Role.DRIVER)
    @Operation(summary = "Update my simulated current location")
    public DriverResponse updateLocation(
            @RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody LocationRequest request) {
        return driverService.updateLocation(user.userId(), request.latitude(), request.longitude());
    }

    // ------------------------------------------------ lookups used by passengers / Ride Service

    @GetMapping("/available")
    @RequireRole({Role.PASSENGER, Role.ADMIN, Role.SERVICE})
    @Operation(summary = "Find eligible AVAILABLE drivers",
            description = "Optionally filter by serviceArea and vehicleType. When latitude and longitude "
                    + "(pickup point) are supplied, drivers are sorted nearest first and distanceKm is returned; "
                    + "otherwise the longest-registered driver comes first.")
    public List<EligibleDriverResponse> findEligibleDrivers(
            @RequestParam(name = "serviceArea", required = false) String serviceArea,
            @RequestParam(name = "vehicleType", required = false) VehicleType vehicleType,
            @RequestParam(name = "latitude", required = false) Double latitude,
            @RequestParam(name = "longitude", required = false) Double longitude,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return driverService.findEligibleDrivers(
                new EligibleDriverCriteria(serviceArea, vehicleType, latitude, longitude, limit));
    }

    @GetMapping("/{driverId}")
    @Operation(summary = "Get a driver by id (any authenticated user)")
    public DriverResponse getDriver(@PathVariable("driverId") String driverId) {
        return driverService.getById(driverId);
    }

    @GetMapping
    @RequireRole(Role.ADMIN)
    @Operation(summary = "List all drivers, optionally filtered by availability status")
    public List<DriverResponse> listDrivers(
            @RequestParam(name = "status", required = false) AvailabilityStatus status) {
        return driverService.listAll(status);
    }

    // ------------------------------------------------ service-to-service operations

    @PostMapping("/{driverId}/reserve")
    @RequireRole({Role.SERVICE, Role.ADMIN})
    @Operation(summary = "Reserve a driver for a ride: AVAILABLE -> BUSY (409 if not available)")
    public DriverResponse reserve(@PathVariable("driverId") String driverId) {
        return driverService.reserveDriver(driverId);
    }

    @PostMapping("/{driverId}/release")
    @RequireRole({Role.SERVICE, Role.ADMIN})
    @Operation(summary = "Release a driver after a ride ends or is cancelled: BUSY -> AVAILABLE")
    public DriverResponse release(@PathVariable("driverId") String driverId) {
        return driverService.releaseDriver(driverId);
    }
}