package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertStatus;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        saved.forEach(a -> {
            a.setDispatched(true);
            a.setDispatchedAt(Instant.now());
        });
        alertRepository.saveAll(saved);

        // Broadcast only after the transaction actually commits. Firing the
        // WebSocket push from inside the transaction let the frontend receive
        // (and immediately re-query) an alert before its INSERT was visible to
        // other connections — a stats refresh triggered by that push could
        // race the commit and read a stale, lower total.
        afterCommit(() -> dtos.forEach(dto -> {
            broadcaster.broadcast(dto);
            radioClient.dispatch(dto);
        }));

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

    /**
     * Operator acknowledges an alert — "seen, being handled" (report §3.3.3).
     * Idempotent: acknowledging an already-acknowledged (or resolved) alert
     * just returns it unchanged rather than erroring, so a double-click on
     * the console can't fail.
     */
    @Transactional
    public AlertDto acknowledge(Long id, String operator) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert " + id + " not found"));
        if (a.getStatus() == AlertStatus.NEW) {
            a.setStatus(AlertStatus.ACKNOWLEDGED);
            a.setAcknowledgedAt(Instant.now());
            a.setAcknowledgedBy(resolveOperator(operator));
            alertRepository.save(a);
        }
        AlertDto dto = mapper.toDto(a);
        afterCommit(() -> broadcaster.broadcast(dto));
        return dto;
    }

    /**
     * Operator resolves / closes an alert (report §3.3.3). Acknowledging is
     * not a prerequisite — resolving straight from NEW is allowed and back-
     * fills the acknowledgement metadata so the audit trail stays complete.
     */
    @Transactional
    public AlertDto resolve(Long id, String operator, String note) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert " + id + " not found"));
        if (a.getStatus() != AlertStatus.RESOLVED) {
            Instant now = Instant.now();
            String who = resolveOperator(operator);
            if (a.getAcknowledgedAt() == null) {
                a.setAcknowledgedAt(now);
                a.setAcknowledgedBy(who);
            }
            a.setStatus(AlertStatus.RESOLVED);
            a.setResolvedAt(now);
            a.setResolvedBy(who);
            if (note != null && !note.isBlank()) {
                a.setResolutionNote(note.trim());
            }
            alertRepository.save(a);
        }
        AlertDto dto = mapper.toDto(a);
        afterCommit(() -> broadcaster.broadcast(dto));
        return dto;
    }

    private static String resolveOperator(String operator) {
        return (operator == null || operator.isBlank()) ? "operator" : operator.trim();
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

    /**
     * Run {@code action} after the current transaction commits (so the DB
     * write is durable and visible before we broadcast it), or immediately
     * if there's no active transaction.
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
