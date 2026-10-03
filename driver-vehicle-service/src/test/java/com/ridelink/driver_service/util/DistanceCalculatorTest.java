package com.ridelink.driver_service.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

    @Test
    void samePointIsZeroKilometres() {
        assertThat(DistanceCalculator.kilometres(6.9271, 79.8612, 6.9271, 79.8612)).isZero();
    }

    @Test
    void colomboToKandyIsAboutNinetyFourKilometresInAStraightLine() {
        double km = DistanceCalculator.kilometres(6.9271, 79.8612, 7.2906, 80.6337);
        assertThat(km).isBetween(93.0, 96.0);
    }

    @Test
    void distanceIsSymmetric() {
        double ab = DistanceCalculator.kilometres(6.9271, 79.8612, 7.2906, 80.6337);
        double ba = DistanceCalculator.kilometres(7.2906, 80.6337, 6.9271, 79.8612);
        assertThat(ab).isEqualTo(ba, org.assertj.core.data.Offset.offset(1e-9));
    }
}