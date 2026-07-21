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
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule 0: a genuine hazard signal rather than a behavioral proxy — the
 * webcam's own detection loop tracks each person's bounding box aspect
 * ratio frame to frame and flags it here (as an {@code ANOMALY} event
 * labelled {@value #FALL_LABEL}) when it flips from upright (tall/narrow)
 * to prone (short/wide) within a couple of seconds. That shape change is a
 * possible collapse — a real emergency regardless of how long the person
 * has been on camera, unlike the time-based loitering/escalation rules.
 *
 * <p>Severity does not depend on dwell time or confidence banding: a fall
 * is urgent everywhere, and more urgent still next to a live track or in a
 * restricted area where nobody would be around to help quickly.
 */
@Component
@Order(5)
@RequiredArgsConstructor
public class FallDetectionRule implements CorrelationRule {

    private static final String FALL_LABEL = "person_fallen";

    private final CorrelationProperties props;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (e.getEventType() != CameraEventType.ANOMALY) return List.of();
        if (!FALL_LABEL.equalsIgnoreCase(e.getLabel())) return List.of();

        Zone zone = ctx.matchingZones().isEmpty() ? null : primaryZone(ctx.matchingZones());
        Long zoneId = zone != null ? zone.getId() : null;

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownFallSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.FALL_DETECTED, e.getCameraId(), zoneId, e.getLabel(), cooldownSince)) {
            return List.of();
        }

        boolean dangerZone = zone != null && (zone.getType() == ZoneType.TRACK || zone.getType() == ZoneType.RESTRICTED);
        AlertSeverity severity = dangerZone ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;

        String zoneName = zone != null ? zone.getName() : "unmapped area";
        double conf = e.getConfidence() != null ? e.getConfidence() : 0.0;
        String msg = String.format(
                Locale.ROOT,
                "Possible fall detected — person collapsed in '%s' (cam %s, confidence %d%%)",
                zoneName, e.getCameraId(), (int) Math.round(conf * 100));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "fallDetection");
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", zoneName);
        if (zone != null) details.put("zoneType", zone.getType().name());
        details.put("confidence", round(conf, 3));

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.FALL_DETECTED)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(zone)
                .cameraEvent(e)
                .details(details)
                .build());
    }

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

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
