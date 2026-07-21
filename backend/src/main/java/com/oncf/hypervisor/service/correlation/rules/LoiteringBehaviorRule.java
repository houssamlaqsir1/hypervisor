package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.behavior.LoiteringModelProvider;
import com.oncf.hypervisor.service.behavior.TrackPoint;
import com.oncf.hypervisor.service.behavior.TrajectoryFeatureExtractor;
import com.oncf.hypervisor.service.behavior.TrajectoryFeatures;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule 5: behavior-model based loitering detection.
 *
 * <p>Unlike the other rules, which judge a single frame (is this a person?
 * what zone is it in?), this one looks at a <em>track</em> — the recent
 * position history of the same camera + object label — and asks a trained
 * {@link LoiteringModelProvider logistic regression} whether the movement
 * pattern looks like someone lingering in a small area rather than passing
 * through. That's a genuinely different signal from confidence-based
 * severity: a low-confidence detection repeated in the same spot for ten
 * minutes is a stronger real-world signal than one very-high-confidence
 * single frame.
 *
 * <p>This rule only ever fires in a TRACK/RESTRICTED zone, where lingering
 * compounds a danger that's already real (severity climbs to CRITICAL with
 * model confidence and dwell time). In STATION/NORMAL zones, standing still
 * isn't dangerous no matter how long it goes on, so it stays silent there —
 * dwell time alone never manufactures an alert out of a safe location.
 */
@Component
@Order(25)
@RequiredArgsConstructor
public class LoiteringBehaviorRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.5;
    private static final double CRITICAL_PROBABILITY = 0.85;
    private static final double CRITICAL_DWELL_SEC = 600; // 10 minutes

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;
    private final AlertRepository alertRepository;
    private final LoiteringModelProvider modelProvider;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null || e.getLabel() == null || e.getLabel().isBlank()) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();

        Category category = CameraClassTaxonomy.classify(e.getLabel());
        if (category != Category.PERSON && category != Category.ANIMAL) return List.of();
        if (ctx.matchingZones().isEmpty()) return List.of();

        Instant since = Instant.now().minus(props.loiteringWindowMinutes(), ChronoUnit.MINUTES);
        List<CameraEvent> recentDesc = cameraEventRepository.findTrackHistory(
                e.getCameraId(), e.getLabel(), since, PageRequest.of(0, 200));
        if (recentDesc.size() < props.loiteringMinEvents()) return List.of();

        List<TrackPoint> points = new ArrayList<>(recentDesc.size());
        for (int i = recentDesc.size() - 1; i >= 0; i--) {
            CameraEvent c = recentDesc.get(i);
            points.add(new TrackPoint(c.getLatitude(), c.getLongitude(), c.getOccurredAt().getEpochSecond()));
        }
        TrajectoryFeatures features = TrajectoryFeatureExtractor.extract(points);
        if (features.dwellTimeSec() < props.loiteringMinDwellSec()) return List.of();

        double probability = modelProvider.predict(features);
        if (probability < modelProvider.getThreshold()) return List.of();

        Zone z = primaryZone(ctx.matchingZones());

        // Lingering only compounds a danger that's already real (TRACK/RESTRICTED).
        // In a STATION/NORMAL zone, standing still isn't dangerous no matter how
        // long it goes on, so this rule stays silent there entirely — dwell time
        // never manufactures an alert out of nothing.
        boolean highRiskZone = z.getType() == ZoneType.TRACK || z.getType() == ZoneType.RESTRICTED;
        if (!highRiskZone) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownLoiteringSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.LOITERING, e.getCameraId(), z.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        AlertSeverity severity = severityFor(probability, features.dwellTimeSec());
        String display = CameraClassTaxonomy.display(e);

        String msg = String.format(
                Locale.ROOT,
                "Loitering — %s lingering in '%s' for %s (cam %s, wander radius %.0fm, model confidence %d%%)",
                display, z.getName(), formatDuration((long) features.dwellTimeSec()),
                e.getCameraId(), features.maxRadiusM(), (int) Math.round(probability * 100));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "loiteringBehavior");
        details.put("category", category.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());
        details.put("modelVersion", modelProvider.getVersion());
        details.put("modelProbability", round(probability, 3));
        details.put("modelThreshold", round(modelProvider.getThreshold(), 3));
        details.put("dwellTimeSec", Math.round(features.dwellTimeSec()));
        details.put("maxRadiusM", round(features.maxRadiusM(), 1));
        details.put("pathLengthM", round(features.pathLengthM(), 1));
        details.put("netDisplacementM", round(features.netDisplacementM(), 1));
        details.put("avgSpeedMps", round(features.avgSpeedMps(), 3));
        details.put("wanderRatio", round(features.wanderRatio(), 3));
        details.put("eventCount", features.eventCount());

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.LOITERING)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .details(details)
                .build());
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

    private static AlertSeverity severityFor(double probability, double dwellTimeSec) {
        boolean veryConfident = probability >= CRITICAL_PROBABILITY || dwellTimeSec >= CRITICAL_DWELL_SEC;
        return veryConfident ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
