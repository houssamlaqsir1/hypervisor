package com.oncf.hypervisor.service.correlation;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;

import java.util.List;

/**
 * Data bag passed through the rule chain for a single incoming event.
 * Rules may read the triggering event and the list of zones containing it,
 * and return zero or more AlertDrafts.
 */
public record CorrelationContext(
        CameraEvent cameraEvent,
        SigEvent sigEvent,
        List<Zone> matchingZones
) {
    public static CorrelationContext forCamera(CameraEvent e, List<Zone> zones) {
        return new CorrelationContext(e, null, zones);
    }

    public static CorrelationContext forSig(SigEvent e, List<Zone> zones) {
        return new CorrelationContext(null, e, zones);
    }
}
