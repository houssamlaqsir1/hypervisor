package com.oncf.hypervisor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link DetectionLocator} is what stops every detection from one camera
 * landing on the same point. The assertions below are all about distance:
 * whether two objects seen in one frame end up far enough apart, and in the
 * right direction, for the correlation rules to measure anything real.
 *
 * <p>Casablanca (33.6°N) is used throughout, where a degree of longitude is
 * ~92.7 km rather than 111 km — the cosine correction matters at this
 * latitude and a test that ignored it would pass on a bad implementation.
 */
class DetectionLocatorTest {

    private static final double CAM_LAT = 33.6;
    private static final double CAM_LON = -7.58;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private final DetectionLocator locator = new DetectionLocator();

    private static String payload(double right, double away) {
        return "{\"source\":\"yolov8_detector\",\"offsetM\":[" + right + "," + away + "]}";
    }

    /** Metres between two coordinates, flat-earth — fine over the tens of metres here. */
    private static double metersBetween(DetectionLocator.Located a, DetectionLocator.Located b) {
        double dNorth = (a.latitude() - b.latitude()) * METERS_PER_DEG_LAT;
        double dEast = (a.longitude() - b.longitude())
                * METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CAM_LAT));
        return Math.hypot(dNorth, dEast);
    }

    @Test
    void withoutAnOffsetTheCameraPositionIsKept() {
        // Older detectors and non-YOLO sources send no offset — unchanged behaviour.
        var located = locator.locate(CAM_LAT, CAM_LON, null, "{\"source\":\"legacy\"}");

        assertThat(located.offsetApplied()).isFalse();
        assertThat(located.latitude()).isEqualTo(CAM_LAT);
        assertThat(located.longitude()).isEqualTo(CAM_LON);
    }

    @Test
    void nullPayloadIsSafe() {
        assertThat(locator.locate(CAM_LAT, CAM_LON, null, null).offsetApplied()).isFalse();
    }

    @Test
    void headingNorthPutsAwayToTheNorthAndRightToTheEast() {
        var located = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(10, 20));

        assertThat(located.offsetApplied()).isTrue();
        // 20 m north of the camera.
        assertThat((located.latitude() - CAM_LAT) * METERS_PER_DEG_LAT).isCloseTo(20, within(0.1));
        // 10 m east — with the longitude degree shortened by cos(33.6°).
        double east = (located.longitude() - CAM_LON)
                * METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CAM_LAT));
        assertThat(east).isCloseTo(10, within(0.1));
    }

    @Test
    void headingEastTurnsAwayIntoEast() {
        // Camera pointing east: "20 m further into the scene" is 20 m east,
        // and "10 m to the right of view centre" is 10 m south.
        var located = locator.locate(CAM_LAT, CAM_LON, 90.0, payload(10, 20));

        double north = (located.latitude() - CAM_LAT) * METERS_PER_DEG_LAT;
        double east = (located.longitude() - CAM_LON)
                * METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CAM_LAT));
        assertThat(east).isCloseTo(20, within(0.1));
        assertThat(north).isCloseTo(-10, within(0.1));
    }

    @Test
    void headingOnlyRotatesItNeverChangesDistances() {
        // The whole point: the rules measure distances, and those must not
        // depend on a heading nobody surveyed. Only the compass placement does.
        var origin = locator.locate(CAM_LAT, CAM_LON, null, payload(0, 0));
        double atNorth = metersBetween(locator.locate(CAM_LAT, CAM_LON, 0.0, payload(6, 8)), origin);
        double atEast = metersBetween(locator.locate(CAM_LAT, CAM_LON, 90.0, payload(6, 8)), origin);
        double atOblique = metersBetween(locator.locate(CAM_LAT, CAM_LON, 217.0, payload(6, 8)), origin);

        assertThat(atNorth).isCloseTo(10.0, within(0.05)); // 6-8-10 triangle
        assertThat(atEast).isCloseTo(atNorth, within(0.05));
        assertThat(atOblique).isCloseTo(atNorth, within(0.05));
    }

    @Test
    void twoObjectsInOneFrameGetTwoDifferentPlaces() {
        // The bug this class exists to fix. A bag at view centre and a person
        // 8 m to its right used to be stored at the identical coordinate, so
        // "is anyone near that bag?" could never be answered.
        var bag = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(0, 12));
        var person = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(8, 12));

        assertThat(metersBetween(bag, person)).isCloseTo(8.0, within(0.05));
    }

    @Test
    void aWalkedPathHasRealLength() {
        // What LoiteringBehaviorRule's trajectory features are built from:
        // successive detections of someone pacing must actually differ.
        var start = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(-5, 10));
        var middle = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(0, 10));
        var end = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(5, 10));

        assertThat(metersBetween(start, middle)).isCloseTo(5.0, within(0.05));
        assertThat(metersBetween(start, end)).isCloseTo(10.0, within(0.05));
    }

    @Test
    void implausibleOffsetsAreIgnoredRatherThanTrusted() {
        // A degenerate bounding box can produce a nonsense scale. Throwing the
        // event 40 km across the map would drag it out of its zone entirely,
        // so fall back to the camera's own position instead.
        var located = locator.locate(CAM_LAT, CAM_LON, null, payload(40_000, 5));

        assertThat(located.offsetApplied()).isFalse();
        assertThat(located.latitude()).isEqualTo(CAM_LAT);
    }

    @Test
    void malformedOffsetIsIgnored() {
        assertThat(locator.locate(CAM_LAT, CAM_LON, null, "{\"offsetM\":[\"x\",2]}").offsetApplied())
                .isFalse();
        assertThat(locator.locate(CAM_LAT, CAM_LON, null, "{\"offsetM\":[1]}").offsetApplied())
                .isFalse();
    }

    @Test
    void negativeOffsetsGoLeftAndNearer() {
        var located = locator.locate(CAM_LAT, CAM_LON, 0.0, payload(-4, -6));

        double north = (located.latitude() - CAM_LAT) * METERS_PER_DEG_LAT;
        double east = (located.longitude() - CAM_LON)
                * METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CAM_LAT));
        assertThat(north).isCloseTo(-6, within(0.1));
        assertThat(east).isCloseTo(-4, within(0.1));
    }
}
