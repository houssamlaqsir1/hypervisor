package com.oncf.hypervisor.service.correlation;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the configured chain of {@link CorrelationRule} beans against an
 * incoming event and returns all resulting alert drafts. Rules are ordered
 * by their {@code @Order} annotation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CorrelationEngine {

    private final List<CorrelationRule> rules;
    private final ZoneRepository zoneRepository;
    private static final double DEFAULT_3D_TOLERANCE = 0.0003;

    public List<AlertDraft> process(CameraEvent e) {
        List<Zone> matching = findMatchingZones(e.getLatitude(), e.getLongitude(), e.getElevationM());
        CorrelationContext ctx = CorrelationContext.forCamera(e, matching);
        List<AlertDraft> drafts = runRules(ctx);
        log.debug("Camera event {} produced {} alert drafts (matching zones={})",
                e.getId(), drafts.size(), matching.size());
        return drafts;
    }

    public List<AlertDraft> process(SigEvent e) {
        List<Zone> matching = findMatchingZones(e.getLatitude(), e.getLongitude(), e.getElevationM());
        CorrelationContext ctx = CorrelationContext.forSig(e, matching);
        List<AlertDraft> drafts = runRules(ctx);
        log.debug("SIG event {} produced {} alert drafts", e.getId(), drafts.size());
        return drafts;
    }

    private List<AlertDraft> runRules(CorrelationContext ctx) {
        return rules.stream()
                .flatMap(r -> {
                    try {
                        return r.evaluate(ctx).stream();
                    } catch (RuntimeException ex) {
                        log.error("Rule {} failed", r.getClass().getSimpleName(), ex);
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
    }

    private List<Zone> findMatchingZones(double lat, double lon, Double elevationM) {
        double elevation = elevationM != null ? elevationM : 0.0;
        List<Zone> intersecting = zoneRepository.findIntersecting3d(lat, lon, elevation);
        if (!intersecting.isEmpty()) {
            return intersecting;
        }
        List<Zone> nearby3d = zoneRepository.findMatching3d(lat, lon, elevation, DEFAULT_3D_TOLERANCE);
        if (!nearby3d.isEmpty()) {
            return nearby3d;
        }
        return zoneRepository.findAll().stream()
                .filter(z -> z.contains(lat, lon))
                .toList();
    }
}
