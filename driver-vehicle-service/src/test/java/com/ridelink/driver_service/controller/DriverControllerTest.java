package com.ridelink.driver_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.ridelink.driver_service.dto.DriverProfileRequest;
import com.ridelink.driver_service.dto.DriverResponse;
import com.ridelink.driver_service.dto.EligibleDriverCriteria;
import com.ridelink.driver_service.dto.VehicleResponse;
import com.ridelink.driver_service.exception.GlobalExceptionHandler;
import com.ridelink.driver_service.exception.InvalidStateException;
import com.ridelink.driver_service.exception.ResourceNotFoundException;
import com.ridelink.driver_service.model.AvailabilityStatus;
import com.ridelink.driver_service.model.VehicleType;
import com.ridelink.driver_service.security.AuthInterceptor;
import com.ridelink.driver_service.security.JwtService;
import com.ridelink.driver_service.service.DriverService;
import com.ridelink.driver_service.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Exercises the controller, auth interceptor and error handler together, without Spring Boot or MongoDB. */
class DriverControllerTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final String VALID_BODY = """
            {
              "fullName": "Nimal Perera",
              "phoneNumber": "+94771234567",
              "licenseNumber": "B1234567",
              "serviceArea": "Colombo",
              "vehicle": {
                "plateNumber": "CAB-1234",
                "make": "Toyota",
                "model": "Axio",
                "color": "White",
                "vehicleType": "CAR",
                "seatCapacity": 4,
                "manufactureYear": 2020
              }
            }
            """;

    private DriverService driverService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        driverService = mock(DriverService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DriverController(driverService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(new JwtService(SECRET)))
                .build();
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/d1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void requestWithTamperedTokenIsUnauthorized() throws Exception {
        String token = TestTokens.validToken("some-other-secret-some-other-secret-99", "u1", "DRIVER");

        mockMvc.perform(get("/api/v1/drivers/d1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passengerCannotCreateDriverProfile() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", bearer("p1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void driverCreatesProfileAndGetsLocationHeader() throws Exception {
        when(driverService.createProfile(eq("acc-9"), any(DriverProfileRequest.class)))
                .thenReturn(sampleResponse("d9"));

        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", bearer("acc-9", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/drivers/d9"))
                .andExpect(jsonPath("$.id").value("d9"))
                .andExpect(jsonPath("$.availabilityStatus").value("OFFLINE"));
        // the account id comes from the verified token, never from the request body
        verify(driverService).createProfile(eq("acc-9"), any(DriverProfileRequest.class));
    }

    @Test
    void invalidBodyIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", bearer("acc-9", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"\", \"phoneNumber\": \"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.vehicle").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/drivers")
                        .header("Authorization", bearer("acc-9", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDriverIsNotFound() throws Exception {
        when(driverService.getById("nope")).thenThrow(new ResourceNotFoundException("Driver not found: nope"));

        mockMvc.perform(get("/api/v1/drivers/nope").header("Authorization", bearer("p1", "PASSENGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Driver not found: nope"))
                .andExpect(jsonPath("$.path").value("/api/v1/drivers/nope"));
    }

    @Test
    void passengerCanSearchEligibleDrivers() throws Exception {
        when(driverService.findEligibleDrivers(any(EligibleDriverCriteria.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/drivers/available")
                        .param("serviceArea", "Colombo")
                        .param("vehicleType", "CAR")
                        .param("latitude", "6.9344")
                        .param("longitude", "79.8428")
                        .header("Authorization", bearer("p1", "PASSENGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(driverService).findEligibleDrivers(
                new EligibleDriverCriteria("Colombo", VehicleType.CAR, 6.9344, 79.8428, 10));
    }

    @Test
    void invalidVehicleTypeParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/available")
                        .param("vehicleType", "SPACESHIP")
                        .header("Authorization", bearer("p1", "PASSENGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void driverCannotSearchOtherDrivers() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/available").header("Authorization", bearer("d1", "DRIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotReserveDrivers() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/d1/reserve").header("Authorization", bearer("p1", "PASSENGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceRoleCanReserveAndConflictIsReportedAs409() throws Exception {
        when(driverService.reserveDriver("d1")).thenThrow(new InvalidStateException("Driver is BUSY"));

        mockMvc.perform(post("/api/v1/drivers/d1/reserve").header("Authorization", bearer("ride-service", "SERVICE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Driver is BUSY"));
    }

    @Test
    void onlyAdminCanListAllDrivers() throws Exception {
        mockMvc.perform(get("/api/v1/drivers").header("Authorization", bearer("p1", "PASSENGER")))
                .andExpect(status().isForbidden());

        when(driverService.listAll(null)).thenReturn(List.of(sampleResponse("d1")));
        mockMvc.perform(get("/api/v1/drivers").header("Authorization", bearer("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d1"));
    }

    private static String bearer(String subject, String role) {
        return "Bearer " + TestTokens.validToken(SECRET, subject, role);
    }

    private static DriverResponse sampleResponse(String id) {
        Instant now = Instant.parse("2026-10-01T08:00:00Z");
        return new DriverResponse(id, "acc-9", "Nimal Perera", "+94771234567", "B1234567", "Colombo",
                AvailabilityStatus.OFFLINE,
                new VehicleResponse("CAB-1234", "Toyota", "Axio", "White", VehicleType.CAR, 4, 2020),
                null, now, now);
    }
}