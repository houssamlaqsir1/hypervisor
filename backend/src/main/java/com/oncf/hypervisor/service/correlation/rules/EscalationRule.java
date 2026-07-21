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
 * Rule 3: escalation — repeated activity in a zone that is already
 * dangerous (TRACK/RESTRICTED) confirms it's not a one-off, so severity
 * climbs with the count: HIGH at the threshold, CRITICAL at 1.5x it.
 *
 * <p>In a STATION/NORMAL zone there is no danger to confirm — people are
 * supposed to be there — so event volume alone never produces an alert
 * here, no matter how large the count gets. Time/repetition is only ever
 * an amplifier of a real danger signal, never a substitute for one.
 *
 * <p>Only detections above a minimum confidence count towards the
 * threshold, so a single lingering person or a noisy low-confidence stream
 * doesn't rack up dozens of "events" and force a false escalation.
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class EscalationRule implements CorrelationRule {

    /** Below this confidence a detection is treated as noise, not a countable event. */
    private static final double MIN_SIGNIFICANT_CONFIDENCE = 0.5;

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

        long count = cameraEventRepository.countSignificantNearby(
                e.getLatitude(), e.getLongitude(), radiusDeg, since, MIN_SIGNIFICANT_CONFIDENCE);
        int threshold = props.escalationThreshold();
        if (count < threshold) return List.of();

        Zone z = ctx.matchingZones().get(0);

        // Being in a TRACK/RESTRICTED zone at all is already the danger — repeated
        // activity there confirms it's not a one-off. A STATION/NORMAL zone has no
        // danger to confirm: event volume alone is never a real threat, no matter
        // how large the count gets, so this rule stays silent there entirely.
        boolean highRiskZone = z.getType() == ZoneType.TRACK || z.getType() == ZoneType.RESTRICTED;
        if (!highRiskZone) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownEscalationSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.ESCALATION, e.getCameraId(), z.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        AlertSeverity severity = severityFor(count, threshold);

        String dominant = CameraClassTaxonomy.display(e);
        String msg = String.format(
                Locale.ROOT,
                "Escalation — %d significant camera events in zone '%s' over %d min (dominant: %s, cam %s)",
                count, z.getName(), props.escalationWindowMinutes(), dominant, e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "escalation");
        details.put("eventCount", count);
        details.put("threshold", threshold);
        details.put("windowMinutes", props.escalationWindowMinutes());
        details.put("dominantLabel", e.getLabel());
        details.put("displayName", dominant);
        details.put("cameraId", e.getCameraId());
        details.put("zoneName", z.getName());
        details.put("zoneType", z.getType().name());

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.ESCALATION)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    private static AlertSeverity severityFor(long count, int threshold) {
        double ratio = (double) count / threshold;
        return ratio >= 1.5 ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
    }
}
