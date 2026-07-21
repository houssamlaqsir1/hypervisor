package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Camera;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.CameraEventDto;
import com.oncf.hypervisor.dto.CameraEventRequest;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.repository.CameraRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationEngine;
import com.oncf.hypervisor.service.live.LivePositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CameraEventService {

    private final CameraEventRepository repository;
    private final CameraRepository cameraRepository;
    private final LivePositionService livePositionService;
    private final CorrelationEngine engine;
    private final AlertService alertService;
    private final HypervisorMapper mapper;

    public record IngestionResult(CameraEventDto event, List<AlertDto> alerts) {}

    @Transactional
    public IngestionResult ingest(CameraEventRequest req) {
        Instant now = Instant.now();

        // Location priority:
        //   1. Fixed installation registry — a real CCTV camera's GPS is
        //      surveyed once at mount time, not re-declared on every frame.
        //   2. Live position report — a handheld/mobile camera (a phone) has
        //      no fixed installation; it reports its own current GPS from a
        //      page opened directly on the phone (see LivePositionService),
        //      independent of whatever device is viewing the dashboard.
        //   3. Whatever the client sent — last resort for ad-hoc test events.
        Optional<Camera> camera = cameraRepository.findByCameraId(req.cameraId());
        Optional<LivePositionService.Position> live = camera.isEmpty()
                ? livePositionService.current(req.cameraId())
                : Optional.empty();

        Double latitude = camera.map(Camera::getLatitude)
                .or(() -> live.map(LivePositionService.Position::latitude))
                .orElse(req.latitude());
        Double longitude = camera.map(Camera::getLongitude)
                .or(() -> live.map(LivePositionService.Position::longitude))
                .orElse(req.longitude());
        Double elevationM = camera.map(Camera::getElevationM)
                .or(() -> live.map(LivePositionService.Position::elevationM))
                .orElse(req.elevationM());

        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                    "Camera '" + req.cameraId() + "' is not registered, has no live position report, "
                            + "and no latitude/longitude was provided");
        }

        CameraEvent e = CameraEvent.builder()
                .cameraId(req.cameraId())
                .eventType(req.eventType())
                .label(req.label())
                .confidence(req.confidence())
                .latitude(latitude)
                .longitude(longitude)
                .elevationM(elevationM)
                .occurredAt(req.occurredAt() != null ? req.occurredAt() : now)
                .receivedAt(now)
                .rawPayload(mapper.writeJson(req.rawPayload()))
                .build();
        CameraEvent saved = repository.save(e);

        List<AlertDraft> drafts = engine.process(saved);
        List<AlertDto> alerts = alertService.persistAndDispatch(drafts);

        return new IngestionResult(mapper.toDto(saved), alerts);
    }
}
