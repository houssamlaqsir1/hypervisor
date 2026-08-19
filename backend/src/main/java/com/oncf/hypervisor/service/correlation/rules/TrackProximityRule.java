package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.ZoneRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy.Category;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule 6: a person/animal closing in on a live track without being on it
 * yet — "not being careful near the tracks" rather than an outright
 * intrusion. {@link ObjectOnTrackRule} already owns the case where the
 * detection is actually inside the TRACK zone (that's CRITICAL); this rule
 * covers the corridor just outside it, graded by how close the detection
 * is to the track boundary.
 *
 * <p>This is a real, distance-based danger signal — not a time-based proxy
 * — so severity depends purely on proximity to the actual hazard, not on
 * how long anyone's been in frame.
 *
 * <p><b>Two ways of measuring that distance.</b> When the camera has a
 * track footprint configured, the detector measures the gap from the
 * person's feet to the rails directly in the image and reports it as
 * {@code trackDistanceM} — the honest answer to "how far from the track?",
 * graded in metres. Without it the rule falls back to comparing the event's
 * coordinates against the track zone's circle, which is coarser: a zone is
 * a circle on a map and the rails are a line through it, so "outside the
 * circle" is only an approximation of "off the track".
 */
@Component
@Order(22)
@RequiredArgsConstructor
public class TrackProximityRule implements CorrelationRule {

    /** The danger corridor extends this many zone-radii beyond the track's edge. */
    private static final double BUFFER_MULTIPLIER = 2.5;
    private static final double MIN_CONFIDENCE = 0.55;
    /** proximity in [0,1]; at/above this fraction of the way into the corridor it's HIGH, not MEDIUM. */
    private static final double HIGH_PROXIMITY = 0.66;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * Metric danger corridor, used when the detector measures the gap to the
     * rails directly. Inside {@value #DANGER_ZONE_M} m of a live track you are
     * within the swept envelope of a passing train and its slipstream —
     * genuinely dangerous. Out to {@value #WARNING_ZONE_M} m is close enough
     * to be worth telling an operator about; beyond it, standing near a
     * railway is simply standing near a railway.
     */
    private static final double DANGER_ZONE_M = 5.0;
    private static final double WARNING_ZONE_M = 15.0;

    /** Below this overlap the object is beside the rails, not on them (mirrors ObjectOnTrackRule). */
    private static final double NEGLIGIBLE_OVERLAP = 0.02;

    private static final Pattern TRACK_DISTANCE = Pattern.compile(
            "\"trackDistanceM\"\\s*:\\s*([0-9]*\\.?[0-9]+)");
    private static final Pattern TRACK_OVERLAP = Pattern.compile(
            "\"trackOverlap\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    private final CorrelationProperties props;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();

        Category category = CameraClassTaxonomy.classify(e.getLabel());
        if (category != Category.PERSON && category != Category.ANIMAL) return List.of();

        // The camera measured the gap to the rails itself — far better than
        // anything the map circles can say, so use it and stop here.
        Double measuredGapM = measuredValue(e, TRACK_DISTANCE);
        if (measuredGapM != null) {
            return fromMeasuredDistance(ctx, e, category, measuredGapM);
        }

        // Already actually on a track — ObjectOnTrackRule handles that, don't double-alert.
        boolean alreadyOnTrack = ctx.matchingZones().stream().anyMatch(z -> z.getType() == ZoneType.TRACK);
        if (alreadyOnTrack) return List.of();

        Zone nearestTrack = null;
        double bestProximity = 0;
        double bestDistanceM = 0;
        for (Zone z : zoneRepository.findByType(ZoneType.TRACK)) {
            double distance = haversineMeters(z.getCenterLat(), z.getCenterLon(), e.getLatitude(), e.getLongitude());
            double bufferOuter = z.getRadiusM() * BUFFER_MULTIPLIER;
            if (distance > z.getRadiusM() && distance <= bufferOuter) {
                double proximity = 1.0 - (distance - z.getRadiusM()) / (bufferOuter - z.getRadiusM());
                if (proximity > bestProximity) {
                    bestProximity = proximity;
                    bestDistanceM = distance - z.getRadiusM();
                    nearestTrack = z;
                }
            }
        }
        if (nearestTrack == null) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownTrackProximitySec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.TRACK_PROXIMITY, e.getCameraId(), nearestTrack.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        AlertSeverity severity = bestProximity >= HIGH_PROXIMITY ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
        String display = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "%s approaching live track '%s' — %.0fm from the track edge (cam %s)",
                display, nearestTrack.getName(), bestDistanceM, e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "trackProximity");
        details.put("category", category.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", nearestTrack.getName());
        details.put("distanceFromEdgeM", round(bestDistanceM, 1));
        details.put("proximity", round(bestProximity, 3));
        details.put("distanceSource", "zoneGeometry");

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.TRACK_PROXIMITY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(nearestTrack)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    /**
     * The camera-measured path: the detector reported an actual distance in
     * metres from this object's feet to the rails, so grade on that.
     */
    private List<AlertDraft> fromMeasuredDistance(
            CorrelationContext ctx, CameraEvent e, Category category, double gapM) {

        // On the rails rather than beside them — ObjectOnTrackRule's business.
        Double overlap = measuredValue(e, TRACK_OVERLAP);
        if (overlap != null && overlap >= NEGLIGIBLE_OVERLAP) return List.of();
        if (gapM > WARNING_ZONE_M) return List.of();

        Zone track = ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.TRACK)
                .findFirst()
                .orElse(null);
        if (track == null) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownTrackProximitySec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.TRACK_PROXIMITY, e.getCameraId(), track.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        AlertSeverity severity = gapM <= DANGER_ZONE_M ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
        String display = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "%s %.1fm from live track '%s' — %s (cam %s)",
                display, gapM, track.getName(),
                gapM <= DANGER_ZONE_M
                        ? "inside the danger zone of a passing train"
                        : "close to the track, monitor",
                e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "trackProximity");
        details.put("category", category.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", track.getName());
        details.put("distanceFromEdgeM", round(gapM, 1));
        details.put("distanceSource", "cameraMeasured");

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.TRACK_PROXIMITY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(track)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    /** Reads one numeric field out of the detector's raw payload, or null if absent. */
    private static Double measuredValue(CameraEvent e, Pattern pattern) {
        String raw = e.getRawPayload();
        if (raw == null) return null;
        Matcher m = pattern.matcher(raw);
        if (!m.find()) return null;
        try {
            double v = Double.parseDouble(m.group(1));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
