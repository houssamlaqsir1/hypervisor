package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Rule 1: human/intrusion detected in a RESTRICTED zone with high confidence
 * -> HIGH severity alert.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class IntrusionInRestrictedZoneRule implements CorrelationRule {

    private static final Set<CameraEventType> INTRUSION_TYPES =
            Set.of(CameraEventType.HUMAN_DETECTED, CameraEventType.INTRUSION);

    private final CorrelationProperties props;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (!INTRUSION_TYPES.contains(e.getEventType())) return List.of();
        if (e.getConfidence() < props.highConfidenceThreshold()) return List.of();

        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.RESTRICTED)
                .map(z -> buildAlert(e, z))
                .toList();
    }

    private AlertDraft buildAlert(CameraEvent e, Zone z) {
        String msg = "Intrusion detected in restricted zone '" + z.getName() + "' (camera "
                + e.getCameraId() + ", confidence " + String.format("%.2f", e.getConfidence()) + ")";
        return AlertDraft.builder()
                .severity(AlertSeverity.HIGH)
                .type(AlertType.INTRUSION)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .build();
    }
}
