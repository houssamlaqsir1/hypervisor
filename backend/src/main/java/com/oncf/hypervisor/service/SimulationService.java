package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.dto.*;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates synthetic events to demo the pipeline without real cameras or SIG.
 */
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final CameraEventService cameraEventService;
    private final SigEventService sigEventService;
    private final ZoneRepository zoneRepository;
    private final Random random = new Random();

    public CameraEventService.IngestionResult simulateCamera(SimulationRequest req) {
        double[] loc = resolveLocation(req);
        CameraEventType type = randomType();
        double confidence = 0.6 + random.nextDouble() * 0.4; // 0.6 - 1.0
        Map<String, Object> raw = new HashMap<>();
        raw.put("simulated", true);
        raw.put("source", "sim");

        CameraEventRequest cr = new CameraEventRequest(
                "CAM-" + (100 + random.nextInt(20)),
                type,
                labelFor(type),
                confidence,
                loc[0],
                loc[1],
                Instant.now(),
                raw
        );
        return cameraEventService.ingest(cr);
    }

    public SigEventService.IngestionResult simulateSig(SimulationRequest req) {
        double[] loc = resolveLocation(req);
        Map<String, Object> meta = new HashMap<>();
        meta.put("simulated", true);
        meta.put("heading", random.nextInt(360));
        meta.put("speed_kmh", random.nextInt(120));

        SigEventRequest sr = new SigEventRequest(
                "SIG-" + (1 + random.nextInt(5)),
                loc[0],
                loc[1],
                req.zoneId(),
                meta,
                Instant.now()
        );
        return sigEventService.ingest(sr);
    }

    public List<AlertDto> simulateIntrusionScenario(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException("Zone " + zoneId + " not found"));

        double lat = zone.getCenterLat();
        double lon = zone.getCenterLon();
        java.util.ArrayList<AlertDto> allAlerts = new java.util.ArrayList<>();

        // SIG sighting first
        allAlerts.addAll(sigEventService.ingest(new SigEventRequest(
                "SIG-SCENARIO", lat, lon, zone.getId(),
                Map.of("simulated", true, "scenario", "intrusion"),
                Instant.now())).alerts());

        // Then several high-confidence camera detections → triggers intrusion + escalation
        for (int i = 0; i < 4; i++) {
            double jitterLat = lat + (random.nextDouble() - 0.5) * 0.0002;
            double jitterLon = lon + (random.nextDouble() - 0.5) * 0.0002;
            CameraEventService.IngestionResult r = cameraEventService.ingest(new CameraEventRequest(
                    "CAM-SCENARIO",
                    i == 1 ? CameraEventType.INTRUSION : CameraEventType.HUMAN_DETECTED,
                    "person",
                    0.85 + random.nextDouble() * 0.14,
                    jitterLat,
                    jitterLon,
                    Instant.now(),
                    Map.of("scenario", "intrusion", "step", i)
            ));
            allAlerts.addAll(r.alerts());
        }
        return allAlerts;
    }

    private double[] resolveLocation(SimulationRequest req) {
        if (req != null && req.latitude() != null && req.longitude() != null) {
            return new double[]{req.latitude(), req.longitude()};
        }
        if (req != null && req.zoneId() != null) {
            Zone z = zoneRepository.findById(req.zoneId())
                    .orElseThrow(() -> new NotFoundException("Zone " + req.zoneId() + " not found"));
            double jitterLat = z.getCenterLat() + (random.nextDouble() - 0.5) * 0.0004;
            double jitterLon = z.getCenterLon() + (random.nextDouble() - 0.5) * 0.0004;
            return new double[]{jitterLat, jitterLon};
        }
        // pick a random existing zone, or default to Casablanca center
        List<Zone> zones = zoneRepository.findAll();
        if (!zones.isEmpty()) {
            Zone z = zones.get(random.nextInt(zones.size()));
            return new double[]{
                    z.getCenterLat() + (random.nextDouble() - 0.5) * 0.001,
                    z.getCenterLon() + (random.nextDouble() - 0.5) * 0.001
            };
        }
        return new double[]{33.5731, -7.5898};
    }

    private CameraEventType randomType() {
        CameraEventType[] vals = CameraEventType.values();
        return vals[random.nextInt(vals.length)];
    }

    private String labelFor(CameraEventType type) {
        return switch (type) {
            case HUMAN_DETECTED, INTRUSION -> "person";
            case OBJECT_DETECTED -> random.nextBoolean() ? "bag" : "debris";
            case ANOMALY -> "unknown";
        };
    }
}
