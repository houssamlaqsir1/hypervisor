package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;

import java.time.Instant;

/**
 * Shared builders for the correlation-rule unit tests. Keeps each test
 * focused on the one thing it asserts (severity band, zone gating, …)
 * instead of repeating entity setup.
 */
final class RuleTestFixtures {

    private RuleTestFixtures() {
    }

    /**
     * Correlation properties matching the production defaults in
     * {@code application.yaml}.
     *
     * <p>Most fields could be passed as 0 and let the record's compact
     * constructor substitute a default, but the night-window hours are
     * stated explicitly on purpose: 0 is a <em>valid</em> hour, so it would
     * survive validation and silently produce a 00:00–00:00 window that
     * never matches. Values a test asserts against should be visible in the
     * test, not inferred from a fallback.
     */
    static CorrelationProperties defaultProps() {
        return new CorrelationProperties(
                5,      // escalationWindowMinutes
                4,      // escalationThreshold
                0.7,    // highConfidenceThreshold
                3,      // fusionWindowMinutes
                150.0,  // fusionRadiusM
                0.55,   // minFusionScore
                120,    // cooldownIntrusionSec
                120,    // cooldownObjectOnTrackSec
                300,    // cooldownEscalationSec
                120,    // cooldownStationActivitySec
                20,     // loiteringWindowMinutes
                4,      // loiteringMinEvents
                90,     // loiteringMinDwellSec
                600,    // cooldownLoiteringSec
                120,    // cooldownFallSec
                180,    // cooldownTrackProximitySec
                5,      // baggageUnattendedMinutes
                3,      // baggageMinSightings
                600,    // cooldownBaggageSec
                2,      // crowdWindowMinutes
                25,     // crowdThreshold
                300,    // cooldownCrowdSec
                23,     // nightStartHour
                5,      // nightEndHour
                "Africa/Casablanca",
                600     // cooldownNightSec
        );
    }

    static Zone zone(long id, String name, ZoneType type, double lat, double lon, double radiusM) {
        return Zone.builder()
                .id(id)
                .name(name)
                .type(type)
                .centerLat(lat)
                .centerLon(lon)
                .radiusM(radiusM)
                .build();
    }

    static CameraEvent event(CameraEventType type, String label, double confidence, double lat, double lon) {
        Instant now = Instant.now();
        return CameraEvent.builder()
                .id(1L)
                .cameraId("CAM-TEST")
                .eventType(type)
                .label(label)
                .confidence(confidence)
                .latitude(lat)
                .longitude(lon)
                .elevationM(0.0)
                .occurredAt(now)
                .receivedAt(now)
                .build();
    }
}
