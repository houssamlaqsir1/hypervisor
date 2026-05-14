package com.oncf.hypervisor.service.correlation;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import lombok.Builder;

import java.util.Map;

/**
 * In-memory draft produced by a {@link CorrelationRule}. The engine persists
 * it via AlertService which in turn dispatches to the Alert Radio sink.
 *
 * <p>{@code details} is an optional structured payload the rule can attach
 * to expose the numeric reasoning behind the alert (fusion score, metric
 * distance, time delta, camera confidence, …). It is serialised to JSON and
 * stored on {@link com.oncf.hypervisor.domain.Alert#getDetails()}.
 */
@Builder
public record AlertDraft(
        AlertSeverity severity,
        AlertType type,
        String message,
        Double latitude,
        Double longitude,
        Zone zone,
        CameraEvent cameraEvent,
        SigEvent sigEvent,
        Map<String, Object> details
) {}
