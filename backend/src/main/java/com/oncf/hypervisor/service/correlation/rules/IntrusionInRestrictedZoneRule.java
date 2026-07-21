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
import java.util.Set;

/**
 * Rule 1: a confident <b>person</b> detected inside a {@link ZoneType#RESTRICTED}
 * zone — unauthorised intrusion.
 *
 * <ul>
 *     <li>Severity is graded on detection confidence, above the
 *     {@code high-confidence-threshold} gate (0.7 by default):
 *     MEDIUM below 0.8 (borderline — worth a look), HIGH below 0.9,
 *     CRITICAL at 0.9+ (clear identification)</li>
 *     <li>Per-camera × zone cooldown of 2 min so a stationary intruder fires once, not every 4 s</li>
 *     <li>Animals in a RESTRICTED zone don't go through this rule (handled by
 *         {@link ObjectOnTrackRule} when on a track, or ignored elsewhere)</li>
 * </ul>
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class IntrusionInRestrictedZoneRule implements CorrelationRule {

    private static final Set<CameraEventType> INTRUSION_TYPES =
            Set.of(CameraEventType.HUMAN_DETECTED, CameraEventType.INTRUSION);
    private static final double HIGH_CONFIDENCE = 0.8;
    private static final double CRITICAL_CONFIDENCE = 0.9;

    private final CorrelationProperties props;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (!INTRUSION_TYPES.contains(e.getEventType())) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < props.highConfidenceThreshold()) {
            return List.of();
        }
        Category category = CameraClassTaxonomy.classify(e.getLabel());
        if (category != Category.PERSON && category != Category.OTHER) {
            // Animals / vehicles / luggage are handled by their own rules.
            // OTHER falls through so legacy INTRUSION events still work even
            // when the FE didn't attach a label.
            return List.of();
        }

        Instant since = Instant.now().minusSeconds(props.cooldownIntrusionSec());
        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.RESTRICTED)
                .filter(z -> !alertRepository.existsRecentByCameraLabelZone(
                        AlertType.INTRUSION, e.getCameraId(), z.getId(), e.getLabel(), since))
                .map(z -> buildAlert(e, z))
                .toList();
    }

    private AlertDraft buildAlert(CameraEvent e, Zone z) {
        double conf = e.getConfidence() != null ? e.getConfidence() : 0.0;
        AlertSeverity severity;
        if (conf >= CRITICAL_CONFIDENCE) {
            severity = AlertSeverity.CRITICAL;
        } else if (conf >= HIGH_CONFIDENCE) {
            severity = AlertSeverity.HIGH;
        } else {
            severity = AlertSeverity.MEDIUM;
        }

        String who = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "Intrusion — %s inside restricted zone '%s' (cam %s, confidence %d%%)",
                who, z.getName(), e.getCameraId(), (int) Math.round(conf * 100));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "intrusion");
        details.put("category", "PERSON");
        details.put("classLabel", e.getLabel());
        details.put("displayName", who);
        details.put("cameraId", e.getCameraId());
        details.put("confidence", round(conf, 3));
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());

        return AlertDraft.builder()
                .severity(severity)
                .type(AlertType.INTRUSION)
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
