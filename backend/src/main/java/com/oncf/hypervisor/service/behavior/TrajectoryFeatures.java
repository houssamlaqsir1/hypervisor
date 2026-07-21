package com.oncf.hypervisor.service.behavior;

/**
 * Numeric summary of a track (ordered sequence of {@link TrackPoint}s for
 * the same camera + object label) used as input to the loitering
 * classifier. {@link #toVector()} defines the exact feature order the
 * model was trained on — see {@link #FEATURE_NAMES}.
 */
public record TrajectoryFeatures(
        int eventCount,
        double dwellTimeSec,
        double maxRadiusM,
        double pathLengthM,
        double netDisplacementM,
        double avgSpeedMps,
        /** netDisplacementM / pathLengthM, clamped to [0,1]. ~0 = looped/stayed put, ~1 = straight transit. */
        double wanderRatio
) {
    public static final String[] FEATURE_NAMES = {
            "dwellTimeSec", "maxRadiusM", "netDisplacementM", "avgSpeedMps", "wanderRatio"
    };

    public double[] toVector() {
        return new double[]{dwellTimeSec, maxRadiusM, netDisplacementM, avgSpeedMps, wanderRatio};
    }
}
