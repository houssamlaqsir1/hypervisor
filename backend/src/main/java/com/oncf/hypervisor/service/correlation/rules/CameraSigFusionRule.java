package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.repository.SigEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cross-source fusion: when a camera detection and a SIG event happen close
 * in space <em>and</em> time we compute a normalised confidence score and,
 * if the score clears {@link CorrelationProperties#minFusionScore()}, raise
 * a FUSION alert with the numeric reasoning attached.
 *
 * <p>This rule replaces the previous binary "inside the radius / window =
 * alert" logic so the operator sees <em>why</em> the system thinks the two
 * feeds describe the same event. The score is a weighted blend of:
 * <ul>
 *     <li><b>proximity</b> — 1 − (distanceM / effectiveRadiusM), 3D when
 *     both events report elevation</li>
 *     <li><b>recency</b> — 1 − (timeDeltaSec / fusionWindowSec)</li>
 *     <li><b>camera confidence</b> — already a 0..1 score from the AI</li>
 *     <li><b>zone weight</b> — 1.0 for TRACK / RESTRICTED, 0.8 for STATION,
 *     0.6 for NORMAL</li>
 * </ul>
 *
 * <p>Severity is derived from the score and the zone type, so a same-room
 * pair on a TRACK fires CRITICAL while a near-miss in a STATION fires
 * MEDIUM. A per-triple cooldown (cameraId × sigSourceId × zoneId) prevents
 * the same camera↔SIG pair from spamming the console while they are still
 * co-present.
 */
@Component
@Order(15)
@RequiredArgsConstructor
@Slf4j
public class CameraSigFusionRule implements CorrelationRule {

    private static final int MAX_LOOKBACK = 8;

    /**
     * Guard against stale operation-console leftovers: if either side comes
     * from the operations generator, only fuse events that are close in time.
     */
    private static final long OPERATION_PAIR_MAX_SECONDS = 60;

    /** Earth radius for the haversine surface distance, metres. */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 1 degree of latitude ~= 111 km — used for the SQL pre-filter only. */
    private static final double DEG_PER_METER = 1.0 / 111_000.0;

    // Weights blended into the final score. Sum to 1.0.
    private static final double W_PROXIMITY = 0.35;
    private static final double W_RECENCY = 0.25;
    private static final double W_CONFIDENCE = 0.25;
    private static final double W_ZONE = 0.15;

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;
    private final SigEventRepository sigEventRepository;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        if (ctx.matchingZones().isEmpty()) return List.of();

        Instant since = Instant.now().minus(props.fusionWindowMinutes(), ChronoUnit.MINUTES);
        double effectiveRadiusM = effectiveRadiusMeters(ctx);
        // Coarse SQL pre-filter: pull candidates inside a generous bounding
        // box around the point, then we refine with real haversine + scoring
        // in Java. Multiply by 2 so we don't miss pairs sitting at the edge.
        double radiusDeg = (effectiveRadiusM * 2.0) * DEG_PER_METER;
        Pageable limit = PageRequest.of(0, MAX_LOOKBACK);

        if (ctx.cameraEvent() != null) {
            CameraEvent cam = ctx.cameraEvent();
            if (alertRepository.existsByTypeAndCameraEvent_Id(AlertType.FUSION, cam.getId())) {
                return List.of();
            }
            List<SigEvent> sigs = sigEventRepository.findNearbyRecent(
                    cam.getLatitude(), cam.getLongitude(), radiusDeg, since, limit);

            return bestPair(cam, sigs, ctx.matchingZones(), effectiveRadiusM, true)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        if (ctx.sigEvent() != null) {
            SigEvent sig = ctx.sigEvent();
            if (alertRepository.existsByTypeAndSigEvent_Id(AlertType.FUSION, sig.getId())) {
                return List.of();
            }
            List<CameraEvent> cams = cameraEventRepository.findNearbyRecent(
                    sig.getLatitude(), sig.getLongitude(), radiusDeg, since, limit);

            CameraEvent best = null;
            SigEvent bestSig = sig;
            FusionScore bestScore = null;
            Zone bestZone = ctx.matchingZones().get(0);

            for (CameraEvent cam : cams) {
                if (alertRepository.existsByTypeAndCameraEvent_Id(AlertType.FUSION, cam.getId())) {
                    continue;
                }
                if (!isAcceptablePair(cam, sig)) continue;

                Zone zone = pickPrimaryZone(ctx.matchingZones(), cam, sig);
                FusionScore score = score(cam, sig, zone, effectiveRadiusM);
                if (bestScore == null || score.total > bestScore.total) {
                    best = cam;
                    bestScore = score;
                    bestZone = zone;
                }
            }
            if (best == null || bestScore == null) return List.of();
            return maybeBuild(bestZone, best, bestSig, bestScore, false)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        return List.of();
    }

    /** Use the largest of (zone radius, configured fusion radius) so big zones aren't missed. */
    private double effectiveRadiusMeters(CorrelationContext ctx) {
        double zoneMax = ctx.matchingZones().stream()
                .mapToDouble(Zone::getRadiusM)
                .max()
                .orElse(0.0);
        return Math.max(zoneMax, props.fusionRadiusM());
    }

    private java.util.Optional<AlertDraft> bestPair(CameraEvent cam,
                                                    List<SigEvent> sigs,
                                                    List<Zone> zones,
                                                    double effectiveRadiusM,
                                                    boolean cameraTriggered) {
        SigEvent best = null;
        FusionScore bestScore = null;
        Zone bestZone = zones.get(0);
        for (SigEvent sig : sigs) {
            if (alertRepository.existsByTypeAndSigEvent_Id(AlertType.FUSION, sig.getId())) continue;
            if (!isAcceptablePair(cam, sig)) continue;
            Zone zone = pickPrimaryZone(zones, cam, sig);
            FusionScore s = score(cam, sig, zone, effectiveRadiusM);
            if (bestScore == null || s.total > bestScore.total) {
                best = sig;
                bestScore = s;
                bestZone = zone;
            }
        }
        if (best == null || bestScore == null) return java.util.Optional.empty();
        return maybeBuild(bestZone, cam, best, bestScore, cameraTriggered);
    }

    /**
     * Build the alert if (a) the score clears the configured threshold and
     * (b) we have not just emitted a FUSION alert for the same camera↔SIG
     * pair in the cooldown window.
     */
    private java.util.Optional<AlertDraft> maybeBuild(Zone zone,
                                                      CameraEvent cam,
                                                      SigEvent sig,
                                                      FusionScore s,
                                                      boolean cameraTriggered) {
        if (s.total < props.minFusionScore()) {
            log.debug("Fusion candidate below threshold ({}): cam #{} <-> sig #{} in {} (score={})",
                    String.format(Locale.ROOT, "%.2f", props.minFusionScore()),
                    cam.getId(), sig.getId(), zone.getName(),
                    String.format(Locale.ROOT, "%.2f", s.total));
            return java.util.Optional.empty();
        }
        Instant cooldownSince = Instant.now().minus(props.fusionWindowMinutes(), ChronoUnit.MINUTES);
        if (alertRepository.existsRecentFusionForTriple(
                AlertType.FUSION, cam.getCameraId(), sig.getSourceId(),
                zone.getId(), cooldownSince)) {
            log.debug("Suppressing fusion duplicate within cooldown for {}↔{} in {}",
                    cam.getCameraId(), sig.getSourceId(), zone.getName());
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(build(zone, cam, sig, s, cameraTriggered));
    }

    /** Pick a primary zone for the pair (prefer the most "critical" type). */
    private Zone pickPrimaryZone(List<Zone> zones, CameraEvent cam, SigEvent sig) {
        return zones.stream()
                .min((a, b) -> Integer.compare(zonePriority(a), zonePriority(b)))
                .orElse(zones.get(0));
    }

    /** Lower = more critical (used as tiebreaker for picking the primary zone). */
    private int zonePriority(Zone z) {
        return switch (z.getType()) {
            case TRACK -> 0;
            case RESTRICTED -> 1;
            case STATION -> 2;
            case NORMAL -> 3;
        };
    }

    private double zoneWeight(Zone z) {
        return switch (z.getType()) {
            case TRACK, RESTRICTED -> 1.0;
            case STATION -> 0.8;
            case NORMAL -> 0.6;
        };
    }

    /**
     * Compute the normalised fusion score for a camera↔SIG candidate pair.
     * All four sub-scores are clamped to [0, 1] before blending.
     */
    private FusionScore score(CameraEvent cam, SigEvent sig, Zone zone, double effectiveRadiusM) {
        double dist = distanceMeters(
                cam.getLatitude(), cam.getLongitude(), nullSafe(cam.getElevationM()),
                sig.getLatitude(), sig.getLongitude(), nullSafe(sig.getElevationM()));
        // Twice the zone radius is the soft outer edge of "still plausibly the
        // same event" — beyond that proximity score goes to 0.
        double proxRef = Math.max(effectiveRadiusM * 2.0, 50.0);
        double proximity = clamp01(1.0 - (dist / proxRef));

        long deltaSeconds = Math.abs(ChronoUnit.SECONDS.between(cam.getOccurredAt(), sig.getOccurredAt()));
        double windowSec = Math.max(props.fusionWindowMinutes() * 60.0, 1.0);
        double recency = clamp01(1.0 - (deltaSeconds / windowSec));

        double confidence = clamp01(cam.getConfidence() != null ? cam.getConfidence() : 0.5);
        double zoneW = zoneWeight(zone);

        double total = W_PROXIMITY * proximity
                + W_RECENCY * recency
                + W_CONFIDENCE * confidence
                + W_ZONE * zoneW;
        return new FusionScore(total, proximity, recency, confidence, zoneW, dist, deltaSeconds);
    }

    private AlertDraft build(Zone zone, CameraEvent cam, SigEvent sig, FusionScore s, boolean cameraTriggered) {
        boolean criticalZone = zone.getType() == ZoneType.RESTRICTED || zone.getType() == ZoneType.TRACK;
        AlertSeverity severity;
        if (s.total >= 0.85 || (criticalZone && s.total >= 0.7)) {
            severity = AlertSeverity.CRITICAL;
        } else if (s.total >= 0.65) {
            severity = AlertSeverity.HIGH;
        } else {
            severity = AlertSeverity.MEDIUM;
        }

        String label = cam.getLabel() != null ? cam.getLabel() : cam.getEventType().name();
        String msg = String.format(
                Locale.ROOT,
                "FUSION (score %.2f) in '%s': camera %s (%s, conf %.2f) ↔ SIG %s — %s apart, Δt %s",
                s.total, zone.getName(), cam.getCameraId(), label,
                cam.getConfidence() != null ? cam.getConfidence() : 0.0,
                sig.getSourceId(),
                formatMetric(s.distanceM),
                formatDuration(s.deltaSeconds));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fusionScore", round(s.total, 3));
        details.put("severity", severity.name());
        details.put("trigger", cameraTriggered ? "camera" : "sig");
        details.put("zoneName", zone.getName());
        details.put("zoneType", zone.getType().name());
        details.put("zoneWeight", round(s.zoneWeight, 3));
        details.put("distanceM", round(s.distanceM, 1));
        details.put("timeDeltaSec", s.deltaSeconds);
        details.put("cameraConfidence", round(cam.getConfidence() != null ? cam.getConfidence() : 0.0, 3));
        details.put("subScores", Map.of(
                "proximity", round(s.proximity, 3),
                "recency", round(s.recency, 3),
                "confidence", round(s.confidence, 3),
                "zone", round(s.zoneWeight, 3)
        ));
        details.put("camera", Map.of(
                "id", cam.getCameraId(),
                "eventId", cam.getId(),
                "label", label,
                "lat", cam.getLatitude(),
                "lon", cam.getLongitude(),
                "elevationM", nullSafe(cam.getElevationM())
        ));
        details.put("sig", Map.of(
                "sourceId", sig.getSourceId(),
                "eventId", sig.getId(),
                "lat", sig.getLatitude(),
                "lon", sig.getLongitude(),
                "elevationM", nullSafe(sig.getElevationM())
        ));

        log.debug("Fusion fired: cam #{} <-> sig #{} in {} score={} dist={}m Δt={}s severity={}",
                cam.getId(), sig.getId(), zone.getName(),
                String.format(Locale.ROOT, "%.2f", s.total),
                String.format(Locale.ROOT, "%.1f", s.distanceM),
                s.deltaSeconds, severity);

        return AlertDraft.builder()
                .severity(severity)
                .type(AlertType.FUSION)
                .message(msg)
                .latitude(cameraTriggered ? cam.getLatitude() : sig.getLatitude())
                .longitude(cameraTriggered ? cam.getLongitude() : sig.getLongitude())
                .zone(zone)
                .cameraEvent(cam)
                .sigEvent(sig)
                .details(details)
                .build();
    }

    /**
     * 3D-aware haversine — surface distance over the WGS84 sphere plus the
     * vertical delta from elevation (in metres) combined as Euclidean. Falls
     * back to plain horizontal when either side has no elevation.
     */
    private static double distanceMeters(double lat1, double lon1, double h1,
                                         double lat2, double lon2, double h2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double surface = 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
        double dh = h2 - h1;
        return Math.sqrt(surface * surface + dh * dh);
    }

    private boolean isAcceptablePair(CameraEvent cam, SigEvent sig) {
        if (cam.getOccurredAt() == null || sig.getOccurredAt() == null) return false;
        boolean operationsPair = isOperationGenerated(cam) || isOperationGenerated(sig);
        if (!operationsPair) return true;
        long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(cam.getOccurredAt(), sig.getOccurredAt()));
        return diffSeconds <= OPERATION_PAIR_MAX_SECONDS;
    }

    private boolean isOperationGenerated(CameraEvent e) {
        String raw = e.getRawPayload();
        return raw != null && raw.contains("\"operationGenerated\":true");
    }

    private boolean isOperationGenerated(SigEvent e) {
        String meta = e.getMetadata();
        return meta != null && meta.contains("\"operationGenerated\":true");
    }

    private static double nullSafe(Double v) {
        return v != null ? v : 0.0;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    private static String formatMetric(double meters) {
        if (meters >= 1000) return String.format(Locale.ROOT, "%.2f km", meters / 1000.0);
        return String.format(Locale.ROOT, "%.0f m", meters);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " s";
        long m = seconds / 60;
        long s = seconds % 60;
        return m + "m " + s + "s";
    }

    /** Plain holder so we can pass score + breakdown out of {@link #score}. */
    private record FusionScore(
            double total,
            double proximity,
            double recency,
            double confidence,
            double zoneWeight,
            double distanceM,
            long deltaSeconds
    ) {}
}
