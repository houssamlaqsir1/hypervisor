package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.SigEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.SigEventDto;
import com.oncf.hypervisor.dto.SigEventRequest;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.SigEventRepository;
import com.oncf.hypervisor.repository.ZoneRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SigEventService {

    private final SigEventRepository repository;
    private final ZoneRepository zoneRepository;
    private final CorrelationEngine engine;
    private final AlertService alertService;
    private final HypervisorMapper mapper;

    public record IngestionResult(SigEventDto event, List<AlertDto> alerts) {}

    @Transactional
    public IngestionResult ingest(SigEventRequest req) {
        Zone zone = null;
        if (req.zoneId() != null) {
            zone = zoneRepository.findById(req.zoneId())
                    .orElseThrow(() -> new NotFoundException("Zone " + req.zoneId() + " not found"));
        }
        Instant now = Instant.now();
        SigEvent e = SigEvent.builder()
                .sourceId(req.sourceId())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .elevationM(req.elevationM())
                .trackLevel(req.trackLevel())
                .zone(zone)
                .metadata(mapper.writeJson(req.metadata()))
                .occurredAt(req.occurredAt() != null ? req.occurredAt() : now)
                .receivedAt(now)
                .build();
        SigEvent saved = repository.save(e);

        List<AlertDraft> drafts = engine.process(saved);
        List<AlertDto> alerts = alertService.persistAndDispatch(drafts);

        return new IngestionResult(mapper.toDto(saved), alerts);
    }
}
