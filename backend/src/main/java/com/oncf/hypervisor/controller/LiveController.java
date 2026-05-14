package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.dto.CameraEventRequest;
import com.oncf.hypervisor.dto.SigEventRequest;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.repository.ZoneRepository;
import com.oncf.hypervisor.service.CameraEventService;
import com.oncf.hypervisor.service.SigEventService;
import com.oncf.hypervisor.service.live.LiveStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpoints used by the Live Watch page and external camera systems.
 *
 * <ul>
 *     <li>{@code GET  /api/live/status}     — banner metrics (last poll, counts).</li>
 *     <li>{@code POST /api/live/webcam}     — real browser webcam detection
 *         (TensorFlow.js COCO-SSD running on the user's device).</li>
 *     <li>{@code POST /api/live/gps}        — real browser geolocation reading.</li>
 *     <li>{@code GET  /api/live/ip-camera}  — webhook for IP cameras / phone
 *         apps that send detection events as a simple URL hit
 *         (Xeoma "HTTP Request Sender", Agent DVR, Frigate, …).</li>
 *     <li>{@code POST /api/live/ip-camera}  — same, but with a JSON body for
 *         systems that prefer it.</li>
 * </ul>
 *
 * <p>All POST endpoints reuse the existing {@link CameraEventService} /
 * {@link SigEventService} ingestion paths so the correlation engine + fusion
 * rule run identically to the manual / drill flows.
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

    /** Casablanca city center — fallback when caller doesn't pin to a zone or coords. */
    private static final double DEFAULT_LAT = 33.5731;
    private static final double DEFAULT_LON = -7.5898;
    /** Reasonable default for AI detections that don't include a confidence score. */
    private static final double DEFAULT_CONFIDENCE = 0.85;

    private final LiveStatusService status;
    private final CameraEventService cameraEventService;
    private final SigEventService sigEventService;
    private final ZoneRepository zoneRepository;

    @GetMapping("/status")
    public LiveStatusService.Snapshot status() {
        return status.snapshot();
    }

    @PostMapping("/webcam")
    public ResponseEntity<CameraEventService.IngestionResult> webcam(
            @Valid @RequestBody CameraEventRequest req) {
        CameraEventService.IngestionResult result = cameraEventService.ingest(req);
        status.recordWebcamEvent();
        status.recordCameraEvent(req.cameraId(), webcamSource(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Inspect the raw payload to figure out whether this webcam event came
     * from the operator's browser, the phone HLS stream, or something else.
     * Used only for the live-cameras heartbeat display.
     */
    private String webcamSource(CameraEventRequest req) {
        if (req == null || req.rawPayload() == null) return "browser";
        Object src = req.rawPayload().get("source");
        return src == null ? "browser" : src.toString();
    }

    /**
     * Lists every AI camera the hypervisor has heard from since boot, with
     * its last-seen timestamp and a stale flag. Useful for a "cameras alive"
     * widget that doesn't depend on the operator having the Live Watch page
     * open.
     */
    @GetMapping("/cameras")
    public LiveCamerasResponse cameras() {
        return new LiveCamerasResponse(
                status.cameraHeartbeats(),
                LiveStatusService.STALE_AFTER_SECONDS);
    }

    public record LiveCamerasResponse(
            java.util.List<LiveStatusService.CameraHeartbeatSnapshot> cameras,
            long staleAfterSeconds
    ) {}

    @PostMapping("/gps")
    public ResponseEntity<SigEventService.IngestionResult> gps(
            @Valid @RequestBody SigEventRequest req) {
        SigEventService.IngestionResult result = sigEventService.ingest(req);
        status.recordGpsEvent();
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Webhook flavour for systems that can only do simple GETs with
     * placeholder substitution (Xeoma's HTTP Request Sender is the textbook
     * example). All params are optional except {@code cameraId} so the
     * operator just plugs in one URL.
     */
    @GetMapping("/ip-camera")
    public CameraEventService.IngestionResult ipCameraGet(
            @RequestParam String cameraId,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) Double confidence,
            @RequestParam(required = false) CameraEventType eventType,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String detector,
            @RequestParam(required = false) String detail) {
        return ingestIpCamera(
                cameraId, label, confidence, eventType,
                zoneId, latitude, longitude, source, detector, detail);
    }

    /** Same semantics as the GET version, but with a JSON body. */
    @PostMapping("/ip-camera")
    public CameraEventService.IngestionResult ipCameraPost(
            @RequestBody(required = false) IpCameraRequest body) {
        if (body == null || body.cameraId() == null || body.cameraId().isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        return ingestIpCamera(
                body.cameraId(), body.label(), body.confidence(), body.eventType(),
                body.zoneId(), body.latitude(), body.longitude(),
                body.source(), body.detector(), body.detail());
    }

    private CameraEventService.IngestionResult ingestIpCamera(
            String cameraId,
            String label,
            Double confidence,
            CameraEventType eventType,
            Long zoneId,
            Double latitude,
            Double longitude,
            String source,
            String detector,
            String detail) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        double[] loc = resolveLocation(latitude, longitude, zoneId);
        CameraEventType type = eventType != null ? eventType : guessType(label);
        double conf = confidence != null ? confidence : DEFAULT_CONFIDENCE;
        String resolvedSource = (source == null || source.isBlank()) ? "ip-camera" : source;

        Map<String, Object> raw = new HashMap<>();
        raw.put("source", resolvedSource);
        if (detector != null && !detector.isBlank()) raw.put("detector", detector);
        if (detail != null && !detail.isBlank()) raw.put("detail", detail);
        if (zoneId != null) raw.put("zoneId", zoneId);

        CameraEventRequest req = new CameraEventRequest(
                cameraId,
                type,
                label,
                conf,
                loc[0],
                loc[1],
                0.0,
                Instant.now(),
                raw);
        CameraEventService.IngestionResult result = cameraEventService.ingest(req);
        status.recordIpCameraEvent(resolvedSource);
        status.recordCameraEvent(cameraId, resolvedSource);
        return result;
    }

    private double[] resolveLocation(Double lat, Double lon, Long zoneId) {
        if (lat != null && lon != null) return new double[]{lat, lon};
        if (zoneId != null) {
            Zone z = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new NotFoundException("Zone " + zoneId + " not found"));
            return new double[]{z.getCenterLat(), z.getCenterLon()};
        }
        return new double[]{DEFAULT_LAT, DEFAULT_LON};
    }

    /**
     * Best-effort mapping from a free-text label (Xeoma sends things like
     * "person", "Object Detector: car") to one of our event-type buckets.
     * Anything we don't recognise falls back to {@code OBJECT_DETECTED},
     * which is the safe non-zero alert path.
     */
    private CameraEventType guessType(String label) {
        if (label == null) return CameraEventType.OBJECT_DETECTED;
        String l = label.toLowerCase();
        if (l.contains("person") || l.contains("human") || l.contains("face") || l.contains("people")) {
            return CameraEventType.HUMAN_DETECTED;
        }
        if (l.contains("intrusion")) return CameraEventType.INTRUSION;
        if (l.contains("anomaly")) return CameraEventType.ANOMALY;
        return CameraEventType.OBJECT_DETECTED;
    }

    public record IpCameraRequest(
            String cameraId,
            String label,
            Double confidence,
            CameraEventType eventType,
            Long zoneId,
            Double latitude,
            Double longitude,
            String source,
            String detector,
            String detail
    ) {}
}
