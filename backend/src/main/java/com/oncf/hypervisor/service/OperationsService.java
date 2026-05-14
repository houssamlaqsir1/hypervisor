package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.TrackLevel;
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
 * Operational helpers to inject camera/SIG events when physical integrations
 * are unavailable or during controlled drills.
 */
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final CameraEventService cameraEventService;
    private final SigEventService sigEventService;
    private final ZoneRepository zoneRepository;
    private final Random random = new Random();

    public CameraEventService.IngestionResult receiveCamera(OperationsRequest req) {
        double[] loc = resolveLocation(req);
        CameraEventType type = randomType();
        double confidence = 0.2 + random.nextDouble() * 0.8; // 0.2 - 1.0
        Map<String, Object> raw = new HashMap<>();
        raw.put("operationGenerated", true);
        raw.put("source", "ops");

        CameraEventRequest cr = new CameraEventRequest(
                "CAM-" + (100 + random.nextInt(20)),
                type,
                labelFor(type),
                confidence,
                loc[0],
                loc[1],
                0.0,
                Instant.now(),
                raw
        );
        return cameraEventService.ingest(cr);
    }

    public SigEventService.IngestionResult receiveSig(OperationsRequest req) {
        double[] loc = resolveLocation(req);
        Map<String, Object> meta = new HashMap<>();
        meta.put("operationGenerated", true);
        meta.put("heading", random.nextInt(360));
        meta.put("speed_kmh", random.nextInt(120));

        SigEventRequest sr = new SigEventRequest(
                "SIG-" + (1 + random.nextInt(5)),
                loc[0],
                loc[1],
                0.0,
                TrackLevel.GROUND,
                req.zoneId(),
                meta,
                Instant.now()
        );
        return sigEventService.ingest(sr);
    }

    public List<AlertDto> runIntrusionOperation(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException("Zone " + zoneId + " not found"));

        double lat = zone.getCenterLat();
        double lon = zone.getCenterLon();
        java.util.ArrayList<AlertDto> allAlerts = new java.util.ArrayList<>();

        // SIG sighting first
        allAlerts.addAll(sigEventService.ingest(new SigEventRequest(
                "SIG-SCENARIO", lat, lon,
                0.0,
                TrackLevel.GROUND,
                zone.getId(),
                Map.of("operationGenerated", true, "scenario", "intrusion"),
                Instant.now())).alerts());

        // Then several high-confidence camera detections -> triggers intrusion + escalation
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
                    0.0,
                    Instant.now(),
                    Map.of("scenario", "intrusion", "step", i)
            ));
            allAlerts.addAll(r.alerts());
        }
        return allAlerts;
    }

    private double[] resolveLocation(OperationsRequest req) {
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
