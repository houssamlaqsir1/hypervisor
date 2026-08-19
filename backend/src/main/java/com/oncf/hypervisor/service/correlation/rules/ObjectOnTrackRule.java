package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
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
 * Rule 2: anything class-relevant detected inside a {@link ZoneType#TRACK}
 * zone — collision / derailment risk.
 *
 * <p>Severity is class-, size-, confidence- and <em>geometry</em>-aware so the
 * alert log mirrors how an operator would react in real life:
 * <ul>
 *     <li><b>Person / Vehicle on track</b> → graded on how much of the object
 *     actually sits on the rails when the detector reports it (see
 *     {@code trackOverlap} below): at/above {@value #MAJOR_OVERLAP} of the
 *     object over the rails the track is blocked →
 *     {@link AlertSeverity#CRITICAL}; a smaller incursion — a car nosing in,
 *     or most of the way back off — is a glancing-impact risk →
 *     {@link AlertSeverity#HIGH}. Without that measurement the rule falls
 *     back to confidence: {@link AlertSeverity#HIGH} below
 *     {@value #CRITICAL_CONFIDENCE}, {@link AlertSeverity#CRITICAL} at or
 *     above it.</li>
 *     <li><b>Large animal</b> (cow, horse) → same as a person: a real
 *     derailment risk</li>
 *     <li><b>Medium animal</b> (dog, sheep) → {@link AlertSeverity#HIGH};
 *     worth slowing or stopping, can damage the train front</li>
 *     <li><b>Small animal</b> (cat, bird) → {@link AlertSeverity#MEDIUM};
 *     an animal-welfare concern worth logging, but not a train hazard —
 *     grading it CRITICAL would train operators to ignore CRITICAL</li>
 *     <li><b>Luggage / left object on track</b> → {@link AlertSeverity#MEDIUM}
 *     below {@value #HIGH_CONFIDENCE} confidence, {@link AlertSeverity#HIGH}
 *     above it (slow approach, inspect)</li>
 *     <li>Other COCO classes (chair, cup, tv, …) are ignored — pure noise.</li>
 * </ul>
 *
 * <p><b>{@code trackOverlap}</b> is the fraction of the detection's bounding
 * box that falls inside the camera's track footprint, measured by the
 * detector (see {@code detector/track_zone.py}) — 0 = clear of the rails,
 * 1 = entirely on them. It is optional: cameras without a configured track
 * polygon simply omit it and this rule behaves exactly as it did before.
 * When it <em>is</em> present and negligible, no alert is raised at all —
 * an object standing beside the rails is not an object on the track.
 *
 * <p>Cooldown: 2 min per (camera, zone, class label) so a stationary
 * intruder doesn't generate dozens of alerts — but an escalation is let
 * through, since a car that was clipping the rails and is now across them
 * is new information rather than a repeat.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class ObjectOnTrackRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.55;
    private static final double HIGH_CONFIDENCE = 0.7;
    private static final double CRITICAL_CONFIDENCE = 0.85;

    /** Below this share of the object over the rails it's bbox jitter, not an incursion. */
    private static final double NEGLIGIBLE_OVERLAP = 0.02;
    /** Half the object or more on the rails — the track is blocked, not brushed. */
    private static final double MAJOR_OVERLAP = 0.5;

    /** Pulls {@code "trackOverlap": 0.42} out of the detector's raw payload. */
    private static final Pattern TRACK_OVERLAP =
            Pattern.compile("\"trackOverlap\"\\s*:\\s*([0-9]*\\.?[0-9]+)");

    private final CorrelationProperties props;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        // ANOMALY is intentionally excluded — handled by LowConfidenceAnomalyRule.
        if (e.getEventType() == CameraEventType.ANOMALY) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();

        Category category = CameraClassTaxonomy.classify(e.getLabel());
        if (category == Category.OTHER) return List.of();

        Double overlap = trackOverlap(e);
        // Measured, and the object is beside the rails rather than on them.
        if (overlap != null && overlap < NEGLIGIBLE_OVERLAP) return List.of();

        Instant since = Instant.now().minusSeconds(props.cooldownObjectOnTrackSec());
        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.TRACK)
                .map(z -> build(e, z, category, overlap))
                .filter(draft -> notSuppressedByCooldown(draft, e, since))
                .toList();
    }

    /**
     * Cooldown, but severity-aware: a repeat at the same or a lower severity
     * is noise, while a worse one means the situation has deteriorated and
     * has to reach the operator now.
     */
    private boolean notSuppressedByCooldown(AlertDraft draft, CameraEvent e, Instant since) {
        Long zoneId = draft.zone() != null ? draft.zone().getId() : null;
        return alertRepository.recentSeveritiesByCameraLabelZone(
                        AlertType.OBJECT_ON_TRACK, e.getCameraId(), zoneId, e.getLabel(), since)
                .stream()
                .noneMatch(seen -> seen.rank() >= draft.severity().rank());
    }

    /** The detector's measured track overlap, or null if this camera doesn't report one. */
    private static Double trackOverlap(CameraEvent e) {
        String raw = e.getRawPayload();
        if (raw == null) return null;
        Matcher m = TRACK_OVERLAP.matcher(raw);
        if (!m.find()) return null;
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(m.group(1))));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AlertDraft build(CameraEvent e, Zone z, Category cat, Double overlap) {
        double conf = e.getConfidence() != null ? e.getConfidence() : 0.0;
        String display = CameraClassTaxonomy.display(e);

        boolean criticalConfidence = conf >= CRITICAL_CONFIDENCE;
        boolean highConfidence = conf >= HIGH_CONFIDENCE;
        // Geometry beats confidence when we have it: *how much* of the object
        // is on the rails decides whether the train can pass at all.
        boolean blocking = overlap != null ? overlap >= MAJOR_OVERLAP : criticalConfidence;

        AlertSeverity severity;
        String tail;
        switch (cat) {
            case PERSON -> {
                severity = blocking ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
                tail = blocking
                        ? "immediate collision risk — STOP TRAIN"
                        : (overlap != null
                                ? "partly on the rails — impact risk, slow and confirm"
                                : "possible person on track — needs visual confirmation");
            }
            case ANIMAL -> {
                // Railway risk scales with the animal's mass, not with "it's
                // an animal": a cow can derail a train, a cat cannot.
                switch (CameraClassTaxonomy.animalSize(e.getLabel())) {
                    case LARGE -> {
                        severity = blocking ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
                        tail = blocking
                                ? "large animal on rails — derailment risk, STOP TRAIN"
                                : (overlap != null
                                        ? "large animal partly on the rails — slow and confirm"
                                        : "possible large animal on track — needs visual confirmation");
                    }
                    case SMALL -> {
                        severity = AlertSeverity.MEDIUM;
                        tail = "small animal on rails — no derailment risk, monitor";
                    }
                    default -> {
                        severity = AlertSeverity.HIGH;
                        tail = "animal on rails — slow approach, risk of impact damage";
                    }
                }
            }
            case VEHICLE -> {
                severity = blocking ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
                tail = blocking
                        ? "track blocked — emergency stop required"
                        : (overlap != null
                                // Entering or clearing the crossing: a train would
                                // still catch it, but it isn't a wall on the rails.
                                ? "partly fouling the track — glancing-impact risk, slow the train"
                                : "possible obstruction — needs visual confirmation");
            }
            case LUGGAGE -> {
                severity = highConfidence ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
                tail = "abandoned object on rails — slow approach & inspect";
            }
            default -> {
                severity = highConfidence ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
                tail = "object on rails — inspect";
            }
        }

        String coverage = overlap == null ? ""
                : String.format(Locale.ROOT, ", %d%% on the rails", (int) Math.round(overlap * 100));
        String msg = String.format(
                Locale.ROOT,
                "%s on track '%s' (cam %s, confidence %d%%%s) — %s",
                display, z.getName(), e.getCameraId(), (int) Math.round(conf * 100), coverage, tail);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "objectOnTrack");
        details.put("category", cat.name());
        if (cat == Category.ANIMAL) {
            details.put("animalSize", CameraClassTaxonomy.animalSize(e.getLabel()).name());
        }
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("confidence", round(conf, 3));
        if (overlap != null) {
            details.put("trackOverlap", round(overlap, 3));
            details.put("fouling", overlap >= MAJOR_OVERLAP ? "BLOCKING" : "PARTIAL");
        }
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());

        return AlertDraft.builder()
                .severity(severity)
                .type(AlertType.OBJECT_ON_TRACK)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .details(details)
                .build();
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
