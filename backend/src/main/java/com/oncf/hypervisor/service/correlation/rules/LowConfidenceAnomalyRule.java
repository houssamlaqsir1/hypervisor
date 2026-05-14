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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule 4: an explicit {@link CameraEventType#ANOMALY} reported by a
 * camera/edge — typically the operator manually flagged a frame, or an
 * upstream model flagged unusual behaviour.
 *
 * <p>Low-confidence regular detections are NOT alerted here — they're
 * background noise. A camera saying "I think I see a chair" should
 * not wake an operator.
 */
@Component
@Order(40)
public class LowConfidenceAnomalyRule implements CorrelationRule {

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null) return List.of();
        if (e.getEventType() != CameraEventType.ANOMALY) return List.of();

        double conf = e.getConfidence() != null ? e.getConfidence() : 0.0;
        String msg = String.format(
                Locale.ROOT,
                "Anomaly reported by camera %s (confidence %d%%)",
                e.getCameraId(), (int) Math.round(conf * 100));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "anomaly");
        details.put("category", "ANOMALY");
        details.put("classLabel", e.getLabel());
        details.put("cameraId", e.getCameraId());
        details.put("confidence", round(conf, 3));

        return List.of(AlertDraft.builder()
                .severity(AlertSeverity.LOW)
                .type(AlertType.ANOMALY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .cameraEvent(e)
                .details(details)
                .build());
    }

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
