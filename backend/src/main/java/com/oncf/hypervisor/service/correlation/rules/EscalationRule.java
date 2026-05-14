package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy;
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

/**
 * Rule 3: escalation — if at least {@code escalation-threshold} camera
 * events occurred in the same zone within
 * {@code escalation-window-minutes}, emit a single CRITICAL alert
 * (with a cooldown so we don't repeat it every frame).
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class EscalationRule implements CorrelationRule {

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null || ctx.matchingZones().isEmpty()) return List.of();

        Instant since = Instant.now().minus(props.escalationWindowMinutes(), ChronoUnit.MINUTES);
        double maxRadiusM = ctx.matchingZones().stream()
                .mapToDouble(Zone::getRadiusM).max().orElse(100.0);
        double radiusDeg = (maxRadiusM / 111_000.0);

        long count = cameraEventRepository.countNearby(
                e.getLatitude(), e.getLongitude(), radiusDeg, since);
        if (count < props.escalationThreshold()) return List.of();

        Zone z = ctx.matchingZones().get(0);

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownEscalationSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.ESCALATION, e.getCameraId(), z.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        String dominant = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "Escalation — %d camera events in zone '%s' over %d min (dominant: %s, cam %s)",
                count, z.getName(), props.escalationWindowMinutes(), dominant, e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "escalation");
        details.put("eventCount", count);
        details.put("windowMinutes", props.escalationWindowMinutes());
        details.put("dominantLabel", e.getLabel());
        details.put("displayName", dominant);
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());

        return List.of(AlertDraft.builder()
                .severity(AlertSeverity.CRITICAL)
                .type(AlertType.ESCALATION)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .details(details)
                .build());
    }
}
