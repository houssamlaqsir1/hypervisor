package com.oncf.hypervisor.service.behavior;

/**
 * Minimal geodesic helper shared by {@link TrajectoryFeatureExtractor} and
 * the offline model trainer. Deliberately dependency-free (no JPA/Spring)
 * so the exact same code path can run both at training time and at
 * inference time — a model is only trustworthy if the features it saw
 * during training are computed identically to the ones it scores in
 * production.
 */
final class GeoMath {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoMath() {
        /* utility */
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
