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
        int cooldownStationActivitySec,
        /** How far back (minutes) the loitering behavior model looks for a track's history. */
        int loiteringWindowMinutes,
        /** Minimum number of camera events for the same (camera, label) required before scoring. */
        int loiteringMinEvents,
        /** Minimum dwell time (seconds) spanned by the track before scoring — avoids judging a fly-by. */
        int loiteringMinDwellSec,
        /** Cooldown (seconds) between two loitering alerts on the same camera/zone/label. */
        int cooldownLoiteringSec,
        /** Cooldown (seconds) between two fall-detection alerts on the same camera/zone/label. */
        int cooldownFallSec,
        /** Cooldown (seconds) between two track-proximity alerts on the same camera/zone/label. */
        int cooldownTrackProximitySec
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
        if (loiteringWindowMinutes <= 0) loiteringWindowMinutes = 20;
        if (loiteringMinEvents <= 0) loiteringMinEvents = 4;
        if (loiteringMinDwellSec <= 0) loiteringMinDwellSec = 90;
        if (cooldownLoiteringSec <= 0) cooldownLoiteringSec = 600;
        if (cooldownFallSec <= 0) cooldownFallSec = 120;
        if (cooldownTrackProximitySec <= 0) cooldownTrackProximitySec = 180;
    }
}
