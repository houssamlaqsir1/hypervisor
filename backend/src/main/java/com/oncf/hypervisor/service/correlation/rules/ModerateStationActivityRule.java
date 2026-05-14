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
 * Background informational signal — confirms that a station camera is
 * "seeing life" without flooding the operator. Fires at most once every
 * {@value #COOLDOWN_MINUTES} minutes per (camera, zone) and only for
 * PERSON or ANIMAL detections inside a {@link ZoneType#STATION} zone.
 *
 * <p>Stations are full of passengers by design, so this rule is
 * deliberately {@link AlertSeverity#LOW} — it's a heartbeat, not an
 * incident. Higher-severity rules handle the actual incidents.
 */
@Component
@Order(12)
@RequiredArgsConstructor
public class ModerateStationActivityRule implements CorrelationRule {

    private static final Set<CameraEventType> ACTIVITY_TYPES =
            Set.of(CameraEventType.HUMAN_DETECTED, CameraEventType.OBJECT_DETECTED);
    private static final double MIN_CONFIDENCE = 0.55;

    private final CorrelationProperties props;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (!ACTIVITY_TYPES.contains(e.getEventType())) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();

        Category cat = CameraClassTaxonomy.classify(e.getLabel());
        if (cat != Category.PERSON && cat != Category.ANIMAL) return List.of();

        // Skip if a higher-tier rule already qualifies the event (intrusion in RESTRICTED).
        if (e.getConfidence() >= props.highConfidenceThreshold()
                && ctx.matchingZones().stream().anyMatch(z -> z.getType() == ZoneType.RESTRICTED)) {
            return List.of();
        }

        Instant since = Instant.now().minusSeconds(props.cooldownStationActivitySec());
        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.STATION)
                .filter(z -> !alertRepository.existsRecentByCameraLabelZone(
                        AlertType.ANOMALY, e.getCameraId(), z.getId(), e.getLabel(), since))
                .map(z -> buildAlert(e, z, cat))
                .toList();
    }

    private AlertDraft buildAlert(CameraEvent e, Zone z, Category cat) {
        double conf = e.getConfidence();
        String display = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "Routine activity — %s in station '%s' (cam %s, confidence %d%%)",
                display, z.getName(), e.getCameraId(), (int) Math.round(conf * 100));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "stationActivity");
        details.put("category", cat.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("confidence", round(conf, 3));
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());

        return AlertDraft.builder()
                .severity(AlertSeverity.LOW)
                .type(AlertType.ANOMALY)
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
