package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy.Category;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule 8: platform overcrowding.
 *
 * <p>Crowding is a genuine railway safety hazard, not a nuisance: a packed
 * platform is how people get pushed toward — or onto — the track edge, and
 * it is the standard trigger for holding trains or closing access gates.
 * It's also the one case where a <em>high volume</em> of person detections
 * is itself the danger, which is why this rule intentionally does what
 * {@code EscalationRule} refuses to do in a station.
 *
 * <p>The two are complementary, not contradictory:
 * <ul>
 *     <li>{@code EscalationRule} stays silent in STATION zones because
 *     repeated detections of <em>a</em> person there are normal.</li>
 *     <li>This rule fires only when the count crosses a genuine crowding
 *     threshold — an order of magnitude above routine footfall.</li>
 * </ul>
 *
 * <p>Severity scales with how far past the threshold the count goes, and
 * crowding right next to the rails is treated more seriously than crowding
 * in a concourse.
 *
 * <p><b>Headcount source.</b> The YOLO detector reports how many people it
 * actually saw <em>simultaneously</em> in the frame as {@code personCount}
 * on the triggering event's raw payload, and that — not a count of rows in
 * the events table — is what's compared to the threshold. Counting rows
 * would conflate two different things: one person standing still for two
 * minutes gets re-detected every cooldown interval and racks up dozens of
 * rows, which is loitering, not a crowd. {@code personCount} is only
 * missing for detectors that predate this field (or non-YOLO sources), in
 * which case this rule falls back to the old row-count behaviour so it
 * still fires — just without the same precision.
 */
@Component
@Order(32)
@RequiredArgsConstructor
public class CrowdDensityRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.5;

    private static final Set<String> PERSON_LABELS =
            Set.of("person", "human", "people", "pedestrian", "face");

    /** Count people within this radius (degrees, ~111 km/deg) of the triggering detection. */
    private static final double CROWD_RADIUS_DEG = 60.0 / 111_000.0;

    /** Pulls {@code "personCount": 3} out of the detector's raw payload. */
    private static final Pattern PERSON_COUNT =
            Pattern.compile("\"personCount\"\\s*:\\s*([0-9]+)");

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();
        if (CameraClassTaxonomy.classify(e.getLabel()) != Category.PERSON) return List.of();
        if (ctx.matchingZones().isEmpty()) return List.of();

        // Crowding is only meaningful where people are expected to gather.
        Zone zone = ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.STATION || z.getType() == ZoneType.TRACK)
                .findFirst()
                .orElse(null);
        if (zone == null) return List.of();

        long people;
        Integer reportedHeadcount = personCount(e);
        if (reportedHeadcount != null) {
            people = reportedHeadcount;
        } else {
            Instant since = Instant.now().minus(props.crowdWindowMinutes(), ChronoUnit.MINUTES);
            people = cameraEventRepository.countNearbyByLabels(
                    e.getLatitude(), e.getLongitude(), CROWD_RADIUS_DEG,
                    since, PERSON_LABELS, MIN_CONFIDENCE);
        }

        int threshold = props.crowdThreshold();
        if (people < threshold) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownCrowdSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.CROWD_DENSITY, e.getCameraId(), zone.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        AlertSeverity severity = severityFor(zone.getType(), people, threshold);

        String msg = reportedHeadcount != null
                ? String.format(
                        Locale.ROOT,
                        "Crowding in '%s' — %d people in frame at once (threshold %d, cam %s)",
                        zone.getName(), people, threshold, e.getCameraId())
                : String.format(
                        Locale.ROOT,
                        "Crowding in '%s' — %d person detections in %d min (threshold %d, cam %s)",
                        zone.getName(), people, props.crowdWindowMinutes(), threshold, e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "crowdDensity");
        details.put("personDetections", people);
        details.put("headcountSource", reportedHeadcount != null ? "detectorFrameCount" : "eventRowCount");
        details.put("threshold", threshold);
        if (reportedHeadcount == null) {
            details.put("windowMinutes", props.crowdWindowMinutes());
        }
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", zone.getName());
        details.put("zoneType", zone.getType().name());

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.CROWD_DENSITY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(zone)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    /** The detector's own simultaneous-headcount for this frame, or null if it didn't report one. */
    private static Integer personCount(CameraEvent e) {
        String raw = e.getRawPayload();
        if (raw == null) return null;
        Matcher m = PERSON_COUNT.matcher(raw);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Crowding beside the rails is worse than crowding in a concourse, and
     * severity climbs as the count runs further past the threshold.
     */
    private static AlertSeverity severityFor(ZoneType type, long people, int threshold) {
        double ratio = (double) people / threshold;
        if (type == ZoneType.TRACK) {
            return ratio >= 1.5 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
        }
        if (ratio >= 2.0) return AlertSeverity.HIGH;
        return AlertSeverity.MEDIUM;
    }
}
