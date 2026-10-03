package com.ridelink.driver_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ridelink.driver_service.dto.DriverProfileRequest;
import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.EligibleDriverCriteria;
import com.ridelink.driver_service.dto.EligibleDriverResponse;
import com.ridelink.driver_service.dto.VehicleRequest;
import com.ridelink.driver_service.exception.BadRequestException;
import com.ridelink.driver_service.exception.DuplicateResourceException;
import com.ridelink.driver_service.exception.InvalidStateException;
import com.ridelink.driver_service.exception.ResourceNotFoundException;
import com.ridelink.driver_service.model.AvailabilityStatus;
import com.ridelink.driver_service.model.DriverProfile;
import com.ridelink.driver_service.model.GeoLocation;
import com.ridelink.driver_service.model.Vehicle;
import com.ridelink.driver_service.model.VehicleType;
import com.ridelink.driver_service.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class DriverServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    // Pickup point: Colombo Fort (simulated)
    private static final double PICKUP_LAT = 6.9344;
    private static final double PICKUP_LON = 79.8428;

    private DriverRepository repository;
    private DriverServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(DriverRepository.class);
        service = new DriverServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(DriverProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ create

    @Test
    void createProfile_savesOfflineProfileWithNormalisedLicenseAndPlate() {
        when(repository.findByAccountId("acc-1")).thenReturn(Optional.empty());

        DriverResponse response = service.createProfile("acc-1", profileRequest(" b1234567 ", " cab-1234 "));

        ArgumentCaptor<DriverProfile> captor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(repository).save(captor.capture());
        DriverProfile saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getLicenseNumber()).isEqualTo("B1234567");
        assertThat(saved.getVehicle().getPlateNumber()).isEqualTo("CAB-1234");
        assertThat(saved.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.OFFLINE);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.OFFLINE);
    }

    @Test
    void createProfile_rejectsSecondProfileForSameAccount() {
        when(repository.findByAccountId("acc-1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.OFFLINE)));

        assertThatThrownBy(() -> service.createProfile("acc-1", profileRequest("B1234567", "CAB-1234")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createProfile_rejectsDuplicateLicense() {
        when(repository.findByAccountId("acc-1")).thenReturn(Optional.empty());
        when(repository.findByLicenseNumber("B1234567")).thenReturn(Optional.of(driver("other", AvailabilityStatus.OFFLINE)));

        assertThatThrownBy(() -> service.createProfile("acc-1", profileRequest("b1234567", "CAB-1234")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("License");
    }

    @Test
    void createProfile_rejectsDuplicatePlate() {
        when(repository.findByAccountId("acc-1")).thenReturn(Optional.empty());
        when(repository.findByVehiclePlateNumber("CAB-1234")).thenReturn(Optional.of(driver("other", AvailabilityStatus.OFFLINE)));

        assertThatThrownBy(() -> service.createProfile("acc-1", profileRequest("B1234567", "cab-1234")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("plate");
    }

    // ------------------------------------------------------------ read / update profile

    @Test
    void getById_throwsWhenDriverDoesNotExist() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_allowsKeepingOwnLicenseAndPlate() {
        DriverProfile existing = driver("d1", AvailabilityStatus.OFFLINE);
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(existing));
        when(repository.findByLicenseNumber("B1234567")).thenReturn(Optional.of(existing));
        when(repository.findByVehiclePlateNumber("CAB-1234")).thenReturn(Optional.of(existing));

        DriverResponse response = service.updateProfile("acc-d1", profileRequest("B1234567", "CAB-1234"));

        assertThat(response.id()).isEqualTo("d1");
    }

    @Test
    void updateProfile_rejectsPlateOwnedByAnotherDriver() {
        DriverProfile existing = driver("d1", AvailabilityStatus.OFFLINE);
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(existing));
        when(repository.findByVehiclePlateNumber("XYZ-9999")).thenReturn(Optional.of(driver("d2", AvailabilityStatus.OFFLINE)));

        assertThatThrownBy(() -> service.updateProfile("acc-d1", profileRequest("B1234567", "XYZ-9999")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ------------------------------------------------------------ availability

    @Test
    void updateAvailability_toAvailableRequiresLocation() {
        DriverProfile offline = driver("d1", AvailabilityStatus.OFFLINE);
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(offline));

        assertThatThrownBy(() -> service.updateAvailability("acc-d1", AvailabilityStatus.AVAILABLE))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("location");
    }

    @Test
    void updateAvailability_toAvailableSucceedsWhenLocationKnown() {
        DriverProfile offline = driver("d1", AvailabilityStatus.OFFLINE);
        offline.setCurrentLocation(new GeoLocation(PICKUP_LAT, PICKUP_LON, NOW));
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(offline));

        DriverResponse response = service.updateAvailability("acc-d1", AvailabilityStatus.AVAILABLE);

        assertThat(response.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void updateAvailability_driverCannotSetBusyManually() {
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.AVAILABLE)));

        assertThatThrownBy(() -> service.updateAvailability("acc-d1", AvailabilityStatus.BUSY))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateAvailability_isBlockedWhileDriverIsBusy() {
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.BUSY)));

        assertThatThrownBy(() -> service.updateAvailability("acc-d1", AvailabilityStatus.OFFLINE))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("active ride");
    }

    @Test
    void updateLocation_storesCoordinatesWithTimestamp() {
        when(repository.findByAccountId("acc-d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.OFFLINE)));

        DriverResponse response = service.updateLocation("acc-d1", 6.9, 79.8);

        assertThat(response.currentLocation().latitude()).isEqualTo(6.9);
        assertThat(response.currentLocation().longitude()).isEqualTo(79.8);
        assertThat(response.currentLocation().updatedAt()).isEqualTo(NOW);
    }

    // ------------------------------------------------------------ reserve / release

    @Test
    void reserve_movesAvailableDriverToBusy() {
        when(repository.findById("d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.AVAILABLE)));

        assertThat(service.reserveDriver("d1").availabilityStatus()).isEqualTo(AvailabilityStatus.BUSY);
    }

    @ParameterizedTest
    @EnumSource(value = AvailabilityStatus.class, names = {"OFFLINE", "BUSY"})
    void reserve_rejectsDriverThatIsNotAvailable(AvailabilityStatus status) {
        when(repository.findById("d1")).thenReturn(Optional.of(driver("d1", status)));

        assertThatThrownBy(() -> service.reserveDriver("d1")).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void release_returnsBusyDriverToAvailable() {
        when(repository.findById("d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.BUSY)));

        assertThat(service.releaseDriver("d1").availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void release_rejectsDriverThatIsNotBusy() {
        when(repository.findById("d1")).thenReturn(Optional.of(driver("d1", AvailabilityStatus.AVAILABLE)));

        assertThatThrownBy(() -> service.releaseDriver("d1")).isInstanceOf(InvalidStateException.class);
    }

    // ------------------------------------------------------------ eligible drivers

    @Test
    void findEligible_filtersByAreaAndTypeAndSortsNearestFirst() {
        DriverProfile far = located(driver("far", AvailabilityStatus.AVAILABLE), 6.8511, 79.8653);
        DriverProfile near = located(driver("near", AvailabilityStatus.AVAILABLE), 6.9147, 79.8523);
        DriverProfile otherArea = located(driver("kandy", AvailabilityStatus.AVAILABLE), 6.9300, 79.8400);
        otherArea.setServiceArea("Kandy");
        DriverProfile bike = located(driver("bike", AvailabilityStatus.AVAILABLE), 6.9300, 79.8400);
        bike.getVehicle().setVehicleType(VehicleType.MOTORBIKE);
        when(repository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(far, near, otherArea, bike));

        List<EligibleDriverResponse> result = service.findEligibleDrivers(
                new EligibleDriverCriteria("colombo", VehicleType.CAR, PICKUP_LAT, PICKUP_LON, 10));

        assertThat(result).extracting(r -> r.driver().id()).containsExactly("near", "far");
        assertThat(result.get(0).distanceKm()).isLessThan(result.get(1).distanceKm());
    }

    @Test
    void findEligible_ignoresDriversWithoutLocationWhenSearchingByDistance() {
        DriverProfile noLocation = driver("nowhere", AvailabilityStatus.AVAILABLE);
        DriverProfile located = located(driver("here", AvailabilityStatus.AVAILABLE), 6.9147, 79.8523);
        when(repository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(noLocation, located));

        List<EligibleDriverResponse> result = service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, PICKUP_LAT, PICKUP_LON, 10));

        assertThat(result).extracting(r -> r.driver().id()).containsExactly("here");
    }

    @Test
    void findEligible_withoutCoordinatesReturnsNoDistance() {
        when(repository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .thenReturn(List.of(driver("d1", AvailabilityStatus.AVAILABLE)));

        List<EligibleDriverResponse> result = service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, null, null, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).distanceKm()).isNull();
    }

    @Test
    void findEligible_respectsLimit() {
        when(repository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE)).thenReturn(List.of(
                located(driver("a", AvailabilityStatus.AVAILABLE), 6.91, 79.85),
                located(driver("b", AvailabilityStatus.AVAILABLE), 6.92, 79.85),
                located(driver("c", AvailabilityStatus.AVAILABLE), 6.93, 79.85)));

        List<EligibleDriverResponse> result = service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, PICKUP_LAT, PICKUP_LON, 2));

        assertThat(result).hasSize(2);
    }

    @Test
    void findEligible_returnsEmptyListWhenNobodyIsAvailable() {
        when(repository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE)).thenReturn(List.of());

        assertThat(service.findEligibleDrivers(new EligibleDriverCriteria("Colombo", null, null, null, 10))).isEmpty();
    }

    @Test
    void findEligible_rejectsHalfSpecifiedCoordinates() {
        assertThatThrownBy(() -> service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, PICKUP_LAT, null, 10)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void findEligible_rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, 91.0, 79.0, 10)))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 51})
    void findEligible_rejectsLimitOutsideAllowedRange(int limit) {
        assertThatThrownBy(() -> service.findEligibleDrivers(
                new EligibleDriverCriteria(null, null, null, null, limit)))
                .isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------ test data helpers

    private static DriverProfileRequest profileRequest(String license, String plate) {
        return new DriverProfileRequest("Nimal Perera", "+94771234567", license, "Colombo",
                new VehicleRequest(plate, "Toyota", "Axio", "White", VehicleType.CAR, 4, 2020));
    }

    private static DriverProfile driver(String id, AvailabilityStatus status) {
        return DriverProfile.builder()
                .id(id)
                .accountId("acc-" + id)
                .fullName("Test Driver " + id)
                .phoneNumber("+94771234567")
                .licenseNumber("LIC-" + id)
                .serviceArea("Colombo")
                .vehicle(Vehicle.builder().plateNumber("PLATE-" + id).make("Toyota").model("Axio")
                        .color("White").vehicleType(VehicleType.CAR).seatCapacity(4).manufactureYear(2020).build())
                .availabilityStatus(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static DriverProfile located(DriverProfile driver, double latitude, double longitude) {
        driver.setCurrentLocation(new GeoLocation(latitude, longitude, NOW));
        return driver;
    }
}