package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.AlertStatsDto;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.mapper.HypervisorMapper;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.external.AlertRadioClient;
import com.oncf.hypervisor.websocket.AlertBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final HypervisorMapper mapper;
    private final AlertRadioClient radioClient;
    private final AlertBroadcaster broadcaster;

    @Transactional
    public List<AlertDto> persistAndDispatch(List<AlertDraft> drafts) {
        if (drafts.isEmpty()) return List.of();
        Instant now = Instant.now();
        List<Alert> entities = drafts.stream()
                .map(d -> Alert.builder()
                        .severity(d.severity())
                        .type(d.type())
                        .message(d.message())
                        .details(mapper.writeJson(d.details()))
                        .latitude(d.latitude())
                        .longitude(d.longitude())
                        .zone(d.zone())
                        .cameraEvent(d.cameraEvent())
                        .sigEvent(d.sigEvent())
                        .createdAt(now)
                        .dispatched(false)
                        .build())
                .toList();

        List<Alert> saved = alertRepository.saveAll(entities);
        List<AlertDto> dtos = saved.stream().map(mapper::toDto).toList();

        dtos.forEach(dto -> {
            broadcaster.broadcast(dto);
            radioClient.dispatch(dto);
        });

        saved.forEach(a -> {
            a.setDispatched(true);
            a.setDispatchedAt(Instant.now());
        });
        alertRepository.saveAll(saved);

        log.info("Persisted & dispatched {} alert(s)", dtos.size());
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<AlertDto> search(AlertSeverity severity, Instant since, Integer limit) {
        List<Alert> results;
        if (severity != null && since != null) {
            results = alertRepository.findBySeverityAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(severity, since);
        } else if (severity != null) {
            results = alertRepository.findBySeverityOrderByCreatedAtDesc(severity);
        } else if (since != null) {
            results = alertRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since);
        } else {
            results = alertRepository.findByOrderByCreatedAtDesc();
        }
        if (limit != null && limit > 0 && results.size() > limit) {
            results = results.subList(0, limit);
        }
        return results.stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AlertDto getById(Long id) {
        return alertRepository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("Alert " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public AlertStatsDto stats() {
        Map<AlertSeverity, Long> bySev = new EnumMap<>(AlertSeverity.class);
        for (AlertSeverity s : AlertSeverity.values()) bySev.put(s, 0L);
        long total = 0;
        for (Object[] row : alertRepository.countBySeverity()) {
            AlertSeverity s = (AlertSeverity) row[0];
            Long c = (Long) row[1];
            bySev.put(s, c);
            total += c;
        }
        return new AlertStatsDto(total, bySev);
    }
}
