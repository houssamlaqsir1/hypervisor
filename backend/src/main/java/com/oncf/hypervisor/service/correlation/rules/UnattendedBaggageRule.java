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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule 7: a bag that has stayed in place with nobody around it.
 *
 * <p>This is the classic station-security signal — an item left deliberately
 * and walked away from. The distinction that matters is <em>unattended</em>,
 * not "a bag exists": a suitcase next to its owner is a normal Tuesday, the
 * same suitcase with no person near it for several minutes is why stations
 * get evacuated.
 *
 * <p>Two conditions must both hold:
 * <ol>
 *     <li>the bag has been seen repeatedly over
 *     {@code baggage-unattended-minutes} (so it isn't a one-frame fluke or
 *     someone walking past with a rucksack), and</li>
 *     <li>no person has been detected nearby in that same window.</li>
 * </ol>
 *
 * <p>Severity reflects where it was left: a bag abandoned in a RESTRICTED
 * area or on a TRACK is HIGH, in a public station area it is MEDIUM —
 * suspicious and worth an operator walking over, not an evacuation.
 *
 * <p><b>What "nearby" means.</b> The detector sees the bag and the people
 * in the same frame, so it can measure the actual gap between them and
 * report it as {@code nearestPersonM} — the bag is attended when someone
 * is within {@value #ATTENDANCE_RADIUS_M} m of it, which is what an
 * operator means by "someone is with that bag". The field is absent when
 * no person is in frame at all, which is precisely the unattended case.
 * Cameras that don't report it fall back to a coordinate search around the
 * bag; that is weaker, because without per-object positions every
 * detection from one camera shares a point and the search degenerates into
 * "did this camera see anybody recently, anywhere in view".
 */
@Component
@Order(28)
@RequiredArgsConstructor
public class UnattendedBaggageRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.55;

    /** Label variants that all mean "a human is present". */
    private static final Set<String> PERSON_LABELS =
            Set.of("person", "human", "people", "pedestrian", "face");

    /** Radius (degrees, ~111 km/deg) within which a person counts as "attending" the bag. */
    private static final double ATTENDANCE_RADIUS_DEG = 30.0 / 111_000.0;

    /**
     * How close a person has to be, in metres, to count as attending the bag
     * when the camera measures the gap directly. Roughly "within arm's reach
     * plus a step" — beyond it, the bag is not with that person.
     */
    private static final double ATTENDANCE_RADIUS_M = 3.0;

    /** Matches {@code "nearestPersonM":2.4} — metres to the closest person in frame. */
    private static final Pattern NEAREST_PERSON = Pattern.compile(
            "\"nearestPersonM\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    /** Matches {@code "personCount":0} — how many people the camera saw in the same frame. */
    private static final Pattern PERSON_COUNT = Pattern.compile(
            "\"personCount\"\\s*:\\s*([0-9]+)");

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null || e.getLabel() == null || e.getLabel().isBlank()) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();
        if (CameraClassTaxonomy.classify(e.getLabel()) != Category.LUGGAGE) return List.of();
        if (ctx.matchingZones().isEmpty()) return List.of();

        Instant since = Instant.now().minus(props.baggageUnattendedMinutes(), ChronoUnit.MINUTES);

        // (1) Has this bag actually persisted, or did it just flash by?
        List<CameraEvent> sightings = cameraEventRepository.findTrackHistory(
                e.getCameraId(), e.getLabel(), since, PageRequest.of(0, 100));
        if (sightings.size() < props.baggageMinSightings()) return List.of();

        // Require the sightings to actually span the window, not cluster in one second.
        Instant oldest = sightings.stream()
                .map(CameraEvent::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (oldest == null) return List.of();
        long spanSec = ChronoUnit.SECONDS.between(oldest, e.getOccurredAt());
        if (spanSec < props.baggageUnattendedMinutes() * 60L) return List.of();

        // (2) Is anyone standing near it? If so it's attended — not our problem.
        // A camera that reports its frame headcount can answer this about
        // *this bag*; a coordinate search only ever answers it about the
        // camera's whole field of view.
        Integer peopleInFrame = intField(e, PERSON_COUNT);
        boolean cameraMeasured = peopleInFrame != null;
        if (cameraMeasured) {
            if (peopleInFrame > 0) {
                Double nearestPersonM = doubleField(e, NEAREST_PERSON);
                // Someone is in frame; treat them as attending unless the
                // measured gap proves they're too far to be with the bag.
                if (nearestPersonM == null || nearestPersonM <= ATTENDANCE_RADIUS_M) return List.of();
            }
            // peopleInFrame == 0 → nobody in view at all; the bag is alone.
        } else {
            long peopleNearby = cameraEventRepository.countNearbyByLabels(
                    e.getLatitude(), e.getLongitude(), ATTENDANCE_RADIUS_DEG,
                    since, PERSON_LABELS, MIN_CONFIDENCE);
            if (peopleNearby > 0) return List.of();
        }

        Zone zone = primaryZone(ctx.matchingZones());
        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownBaggageSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.UNATTENDED_BAGGAGE, e.getCameraId(), zone.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        boolean sensitiveArea = zone.getType() == ZoneType.RESTRICTED || zone.getType() == ZoneType.TRACK;
        AlertSeverity severity = sensitiveArea ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;

        String display = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "Unattended %s in '%s' — stationary for %s with nobody nearby (cam %s)",
                display.toLowerCase(Locale.ROOT), zone.getName(), formatDuration(spanSec), e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "unattendedBaggage");
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("unattendedSec", spanSec);
        details.put("sightings", sightings.size());
        details.put("peopleNearby", 0);
        details.put("attendanceSource", cameraMeasured ? "cameraMeasured" : "eventProximity");
        if (cameraMeasured) {
            details.put("peopleInFrame", peopleInFrame);
        }
        details.put("zoneName", zone.getName());
        details.put("zoneType", zone.getType().name());

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.UNATTENDED_BAGGAGE)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(zone)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    private static Integer intField(CameraEvent e, Pattern pattern) {
        String raw = e.getRawPayload();
        if (raw == null) return null;
        Matcher m = pattern.matcher(raw);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double doubleField(CameraEvent e, Pattern pattern) {
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

    /** Prefer the most sensitive zone when a point sits inside more than one. */
    private static Zone primaryZone(List<Zone> zones) {
        return zones.stream()
                .min(Comparator.comparingInt(z -> switch (z.getType()) {
                    case TRACK -> 0;
                    case RESTRICTED -> 1;
                    case STATION -> 2;
                    case NORMAL -> 3;
                }))
                .orElse(zones.get(0));
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
