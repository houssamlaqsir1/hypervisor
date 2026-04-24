package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.CameraEventDto;
import com.oncf.hypervisor.dto.CameraEventRequest;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraEventService {

    private final CameraEventRepository repository;
    private final CorrelationEngine engine;
    private final AlertService alertService;
    private final HypervisorMapper mapper;

    public record IngestionResult(CameraEventDto event, List<AlertDto> alerts) {}

    @Transactional
    public IngestionResult ingest(CameraEventRequest req) {
        Instant now = Instant.now();
        CameraEvent e = CameraEvent.builder()
                .cameraId(req.cameraId())
                .eventType(req.eventType())
                .label(req.label())
                .confidence(req.confidence())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .elevationM(req.elevationM())
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
