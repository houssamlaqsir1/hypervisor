package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rule 2: object detected on a TRACK zone -> CRITICAL alert (collision risk).
 */
@Component
@Order(20)
public class ObjectOnTrackRule implements CorrelationRule {

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (e.getEventType() != CameraEventType.OBJECT_DETECTED) return List.of();

        return ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.TRACK)
                .map(z -> build(e, z))
                .toList();
    }

    private AlertDraft build(CameraEvent e, Zone z) {
        String label = e.getLabel() != null ? e.getLabel() : "object";
        String msg = "Object '" + label + "' detected on track '" + z.getName()
                + "' - potential collision risk";
        return AlertDraft.builder()
                .severity(AlertSeverity.CRITICAL)
                .type(AlertType.OBJECT_ON_TRACK)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(z)
                .cameraEvent(e)
                .build();
    }
}
