package com.oncf.hypervisor.service.correlation;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import lombok.Builder;

/**
 * In-memory draft produced by a {@link CorrelationRule}. The engine persists
 * it via AlertService which in turn dispatches to the Alert Radio sink.
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
        SigEvent sigEvent
) {}
