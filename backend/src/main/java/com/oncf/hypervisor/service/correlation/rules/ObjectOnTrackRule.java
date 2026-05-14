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

/**
 * Rule 2: anything class-relevant detected inside a {@link ZoneType#TRACK}
 * zone — collision / derailment risk.
 *
 * <p>Severity is class-aware so the alert log mirrors how an operator
 * would react in real life:
 * <ul>
 *     <li><b>Person / Animal / Vehicle on track</b> → {@link AlertSeverity#CRITICAL}
 *     (immediate stop the train)</li>
 *     <li><b>Luggage / left object on track</b> → {@link AlertSeverity#HIGH}
 *     (slow approach, inspect)</li>
 *     <li>Other COCO classes (chair, cup, tv, …) are ignored — pure noise.</li>
 * </ul>
 *
 * <p>Cooldown: 2 min per (camera, zone, class label) so a stationary
 * intruder doesn't generate dozens of alerts.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class ObjectOnTrackRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.55;

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

        Instant since = Instant.now().minusSeconds(props.cooldownObjectOnTrackSec());
        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.TRACK)
                .filter(z -> !alertRepository.existsRecentByCameraLabelZone(
                        AlertType.OBJECT_ON_TRACK, e.getCameraId(), z.getId(), e.getLabel(), since))
                .map(z -> build(e, z, category))
                .toList();
    }

    private AlertDraft build(CameraEvent e, Zone z, Category cat) {
        double conf = e.getConfidence() != null ? e.getConfidence() : 0.0;
        String display = CameraClassTaxonomy.display(e);

        AlertSeverity severity;
        String tail;
        switch (cat) {
            case PERSON -> {
                severity = AlertSeverity.CRITICAL;
                tail = "immediate collision risk — STOP TRAIN";
            }
            case ANIMAL -> {
                severity = AlertSeverity.CRITICAL;
                tail = "collision / derailment risk";
            }
            case VEHICLE -> {
                severity = AlertSeverity.CRITICAL;
                tail = "track blocked — emergency stop required";
            }
            case LUGGAGE -> {
                severity = AlertSeverity.HIGH;
                tail = "abandoned object on rails — slow approach & inspect";
            }
            default -> {
                severity = AlertSeverity.HIGH;
                tail = "object on rails — inspect";
            }
        }

        String msg = String.format(
                Locale.ROOT,
                "%s on track '%s' (cam %s, confidence %d%%) — %s",
                display, z.getName(), e.getCameraId(), (int) Math.round(conf * 100), tail);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "objectOnTrack");
        details.put("category", cat.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("confidence", round(conf, 3));
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
