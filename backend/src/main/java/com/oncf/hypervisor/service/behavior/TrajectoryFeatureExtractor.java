package com.oncf.hypervisor.service.behavior;

import java.util.List;

/**
 * Turns an ordered (oldest → newest) list of {@link TrackPoint}s into the
 * numeric {@link TrajectoryFeatures} the loitering model reasons about.
 *
 * <p>Used both by {@link com.oncf.hypervisor.service.correlation.rules.LoiteringBehaviorRule}
 * at inference time and by the offline trainer on synthetic data — sharing
 * this code is what makes the trained weights valid at runtime.
 */
public final class TrajectoryFeatureExtractor {

    private TrajectoryFeatureExtractor() {
        /* utility */
    }

    public static TrajectoryFeatures extract(List<TrackPoint> pointsAscending) {
        int n = pointsAscending.size();
        if (n == 0) {
            return new TrajectoryFeatures(0, 0, 0, 0, 0, 0, 0);
        }
        TrackPoint first = pointsAscending.get(0);
        TrackPoint last = pointsAscending.get(n - 1);

        double dwellTimeSec = Math.max(0, last.epochSeconds() - first.epochSeconds());

        double latSum = 0, lonSum = 0;
        for (TrackPoint p : pointsAscending) {
            latSum += p.lat();
            lonSum += p.lon();
        }
        double centroidLat = latSum / n;
        double centroidLon = lonSum / n;

        double maxRadiusM = 0;
        for (TrackPoint p : pointsAscending) {
            maxRadiusM = Math.max(maxRadiusM, GeoMath.haversineMeters(centroidLat, centroidLon, p.lat(), p.lon()));
        }

        double pathLengthM = 0;
        for (int i = 1; i < n; i++) {
            TrackPoint a = pointsAscending.get(i - 1);
            TrackPoint b = pointsAscending.get(i);
            pathLengthM += GeoMath.haversineMeters(a.lat(), a.lon(), b.lat(), b.lon());
        }

        double netDisplacementM = GeoMath.haversineMeters(first.lat(), first.lon(), last.lat(), last.lon());
        double avgSpeedMps = pathLengthM / Math.max(dwellTimeSec, 1.0);
        double wanderRatio = pathLengthM < 0.5 ? 0.0 : Math.min(1.0, netDisplacementM / pathLengthM);

        return new TrajectoryFeatures(n, dwellTimeSec, maxRadiusM, pathLengthM, netDisplacementM, avgSpeedMps, wanderRatio);
    }
}
