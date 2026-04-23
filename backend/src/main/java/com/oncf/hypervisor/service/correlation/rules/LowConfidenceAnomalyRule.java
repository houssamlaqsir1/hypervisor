package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rule 4: anomalies or low-confidence detections outside any zone
 * emit a LOW informational alert so operators still see them in the HMI.
 */
@Component
@Order(40)
public class LowConfidenceAnomalyRule implements CorrelationRule {

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();

        boolean lowConfidence = e.getConfidence() < 0.5;
        boolean isAnomaly = e.getEventType() == CameraEventType.ANOMALY;
        if (!lowConfidence && !isAnomaly) return List.of();
        if (!ctx.matchingZones().isEmpty()) return List.of(); // handled elsewhere

        String msg = (isAnomaly ? "Anomaly" : "Low-confidence detection")
                + " reported by camera " + e.getCameraId()
                + " (" + e.getEventType() + ", conf=" + String.format("%.2f", e.getConfidence()) + ")";

        return List.of(AlertDraft.builder()
                .severity(AlertSeverity.LOW)
                .type(AlertType.ANOMALY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .cameraEvent(e)
                .build());
    }
}
