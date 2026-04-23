package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rule 3: escalation — if >= N camera events occur in the same zone within a
 * configurable time window, emit a CRITICAL escalation alert.
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class EscalationRule implements CorrelationRule {

    private final CorrelationProperties props;
    private final CameraEventRepository cameraEventRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null || ctx.matchingZones().isEmpty()) return List.of();

        Instant since = Instant.now().minus(props.escalationWindowMinutes(), ChronoUnit.MINUTES);
        // approximate nearby radius in degrees from largest matching zone (1 deg ~ 111 km)
        double maxRadiusM = ctx.matchingZones().stream()
                .mapToDouble(Zone::getRadiusM).max().orElse(100.0);
        double radiusDeg = (maxRadiusM / 111_000.0);

        long count = cameraEventRepository.countNearby(
                e.getLatitude(), e.getLongitude(), radiusDeg, since);

        if (count < props.escalationThreshold()) return List.of();

        Zone z = ctx.matchingZones().get(0);
        String msg = "Escalation: " + count + " camera events in zone '" + z.getName()
                + "' in the last " + props.escalationWindowMinutes() + " minutes";

        return List.of(AlertDraft.builder()
                .severity(AlertSeverity.CRITICAL)
                .type(AlertType.ESCALATION)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .build());
    }
}
