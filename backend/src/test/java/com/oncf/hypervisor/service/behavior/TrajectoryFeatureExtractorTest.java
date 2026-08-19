package com.oncf.hypervisor.service.behavior;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loitering model is only as trustworthy as the features it's fed, so
 * these lock down {@link TrajectoryFeatureExtractor}'s core distinctions:
 * a straight transit vs. someone lingering in one spot.
 */
class TrajectoryFeatureExtractorTest {

    // ~1 metre expressed in degrees of latitude, for building synthetic tracks.
    private static final double M_PER_DEG_LAT = 111_320.0;
    private static final double BASE_LAT = 33.6;
    private static final double BASE_LON = -7.58;

    private static TrackPoint pointMetres(double xMeters, double yMeters, long epochSeconds) {
        double lat = BASE_LAT + yMeters / M_PER_DEG_LAT;
        double lon = BASE_LON + xMeters / (M_PER_DEG_LAT * Math.cos(Math.toRadians(BASE_LAT)));
        return new TrackPoint(lat, lon, epochSeconds);
    }

    @Test
    void emptyTrackIsAllZeros() {
        TrajectoryFeatures f = TrajectoryFeatureExtractor.extract(List.of());
        assertThat(f.eventCount()).isZero();
        assertThat(f.dwellTimeSec()).isZero();
        assertThat(f.maxRadiusM()).isZero();
    }

    @Test
    void straightWalkHasHighWanderRatioAndSpeed() {
        // Walk 100 m in a straight line over 100 s ≈ 1 m/s.
        List<TrackPoint> track = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            track.add(pointMetres(i * 10.0, 0, i * 10L));
        }
        TrajectoryFeatures f = TrajectoryFeatureExtractor.extract(track);

        assertThat(f.eventCount()).isEqualTo(11);
        assertThat(f.dwellTimeSec()).isEqualTo(100.0);
        // Net displacement ≈ path length for a straight line → wanderRatio ≈ 1.
        assertThat(f.wanderRatio()).isGreaterThan(0.95);
        assertThat(f.avgSpeedMps()).isBetween(0.8, 1.2);
        assertThat(f.netDisplacementM()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    void lingeringInPlaceHasLowWanderRatioAndSmallRadius() {
        // Bounce within a ~2 m box for 300 s, ending near the start.
        List<TrackPoint> track = List.of(
                pointMetres(0, 0, 0),
                pointMetres(2, 0, 60),
                pointMetres(0, 2, 120),
                pointMetres(-2, 0, 180),
                pointMetres(0, -2, 240),
                pointMetres(0, 0, 300)
        );
        TrajectoryFeatures f = TrajectoryFeatureExtractor.extract(track);

        assertThat(f.dwellTimeSec()).isEqualTo(300.0);
        // Stayed within a couple of metres of the centroid.
        assertThat(f.maxRadiusM()).isLessThan(3.0);
        // Ends where it started → tiny net displacement despite real path length.
        assertThat(f.netDisplacementM()).isLessThan(1.0);
        // Looped back → wanderRatio near 0, the opposite of a transit.
        assertThat(f.wanderRatio()).isLessThan(0.1);
        // Barely moving.
        assertThat(f.avgSpeedMps()).isLessThan(0.2);
    }
}
