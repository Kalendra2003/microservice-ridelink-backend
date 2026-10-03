package com.ridelink.driver_service.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverProfile {

    @Id
    private String id;

    /** Identifier of the account in the Account Service (JWT subject). Stable cross-service reference. */
    @Indexed(unique = true)
    private String accountId;

    private String fullName;
    private String phoneNumber;

    @Indexed(unique = true)
    private String licenseNumber;

    @Indexed
    private String serviceArea;

    private Vehicle vehicle;

    @Indexed
    private AvailabilityStatus availabilityStatus;

    private GeoLocation currentLocation;

    private Instant createdAt;
    private Instant updatedAt;

    /** Optimistic locking: protects against two ride requests reserving the same driver at once. */
    @Version
    private Long version;
}