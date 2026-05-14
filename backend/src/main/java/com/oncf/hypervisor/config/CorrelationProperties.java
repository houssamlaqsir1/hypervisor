package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hypervisor.correlation")
public record CorrelationProperties(
        int escalationWindowMinutes,
        int escalationThreshold,
        double highConfidenceThreshold,
        int fusionWindowMinutes,
        double fusionRadiusM,
        /**
         * Minimum normalised fusion score (0..1) required before
         * {@code CameraSigFusionRule} actually emits a FUSION alert. Lower =
         * more alerts (good for demos / hand-crafted scenarios); higher =
         * only confident camera↔SIG pairings.
         */
        double minFusionScore,
        /** Cooldown (seconds) between two intrusion alerts on the same camera/zone/label. */
        int cooldownIntrusionSec,
        /** Cooldown (seconds) between two object-on-track alerts on the same camera/zone/label. */
        int cooldownObjectOnTrackSec,
        /** Cooldown (seconds) for escalation alerts on the same camera/zone. */
        int cooldownEscalationSec,
        /** Cooldown (seconds) for "station activity" heartbeat alerts. */
        int cooldownStationActivitySec
) {
    public CorrelationProperties {
        if (escalationWindowMinutes <= 0) escalationWindowMinutes = 5;
        if (escalationThreshold <= 0) escalationThreshold = 4;
        if (highConfidenceThreshold <= 0) highConfidenceThreshold = 0.7;
        if (fusionWindowMinutes <= 0) fusionWindowMinutes = 3;
        if (fusionRadiusM <= 0) fusionRadiusM = 150.0;
        if (minFusionScore <= 0 || minFusionScore > 1.0) minFusionScore = 0.55;
        if (cooldownIntrusionSec <= 0) cooldownIntrusionSec = 120;
        if (cooldownObjectOnTrackSec <= 0) cooldownObjectOnTrackSec = 120;
        if (cooldownEscalationSec <= 0) cooldownEscalationSec = 300;
        if (cooldownStationActivitySec <= 0) cooldownStationActivitySec = 900;
    }
}
