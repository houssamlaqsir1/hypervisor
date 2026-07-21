package com.oncf.hypervisor.service.behavior;

/**
 * One position sample of an object's track, stripped down to exactly what
 * the behavior model needs. Kept independent of {@link com.oncf.hypervisor.domain.CameraEvent}
 * so the same feature-extraction code can run outside Spring/JPA (offline
 * training on synthetic data).
 */
public record TrackPoint(double lat, double lon, long epochSeconds) {
}
