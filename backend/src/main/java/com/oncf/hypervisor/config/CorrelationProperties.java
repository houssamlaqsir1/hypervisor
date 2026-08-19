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
        int cooldownTrackProximitySec,

        /* ── unattended baggage ── */
        /** How long a bag must persist with nobody nearby before it counts as abandoned. */
        int baggageUnattendedMinutes,
        /** Minimum sightings of the bag over that period (guards against a one-frame fluke). */
        int baggageMinSightings,
        /** Cooldown (seconds) between two unattended-baggage alerts on the same camera/zone/label. */
        int cooldownBaggageSec,

        /* ── crowd density ── */
        /** Window (minutes) over which person detections are counted for crowding. */
        int crowdWindowMinutes,
        /** Person detections in the window before a station counts as crowded. */
        int crowdThreshold,
        /** Cooldown (seconds) between two crowd alerts on the same camera/zone. */
        int cooldownCrowdSec,

        /* ── night activity ── */
        /** Hour (local) when the "closed" period starts, e.g. 23. */
        int nightStartHour,
        /** Hour (local) when the "closed" period ends, e.g. 5. */
        int nightEndHour,
        /** IANA zone used to interpret those hours (ONCF operates in Africa/Casablanca). */
        String nightZoneId,
        /** Cooldown (seconds) between two night-activity alerts on the same camera/zone/label. */
        int cooldownNightSec
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
        if (cooldownStationActivitySec <= 0) cooldownStationActivitySec = 120;
        if (loiteringWindowMinutes <= 0) loiteringWindowMinutes = 20;
        if (loiteringMinEvents <= 0) loiteringMinEvents = 4;
        if (loiteringMinDwellSec <= 0) loiteringMinDwellSec = 90;
        if (cooldownLoiteringSec <= 0) cooldownLoiteringSec = 600;
        if (cooldownFallSec <= 0) cooldownFallSec = 120;
        if (cooldownTrackProximitySec <= 0) cooldownTrackProximitySec = 180;
        if (baggageUnattendedMinutes <= 0) baggageUnattendedMinutes = 5;
        if (baggageMinSightings <= 0) baggageMinSightings = 3;
        if (cooldownBaggageSec <= 0) cooldownBaggageSec = 600;
        if (crowdWindowMinutes <= 0) crowdWindowMinutes = 2;
        if (crowdThreshold <= 0) crowdThreshold = 25;
        if (cooldownCrowdSec <= 0) cooldownCrowdSec = 300;
        if (nightStartHour < 0 || nightStartHour > 23) nightStartHour = 23;
        if (nightEndHour < 0 || nightEndHour > 23) nightEndHour = 5;
        if (nightZoneId == null || nightZoneId.isBlank()) nightZoneId = "Africa/Casablanca";
        if (cooldownNightSec <= 0) cooldownNightSec = 600;
    }
}
