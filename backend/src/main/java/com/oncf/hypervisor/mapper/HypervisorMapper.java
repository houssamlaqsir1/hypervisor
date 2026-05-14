package com.oncf.hypervisor.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncf.hypervisor.domain.*;
import com.oncf.hypervisor.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HypervisorMapper {

    private final ObjectMapper objectMapper;

    public CameraEventDto toDto(CameraEvent e) {
        return new CameraEventDto(
                e.getId(),
                e.getCameraId(),
                e.getEventType(),
                e.getLabel(),
                e.getConfidence(),
                e.getLatitude(),
                e.getLongitude(),
                e.getElevationM(),
                e.getOccurredAt(),
                e.getReceivedAt()
        );
    }

    public SigEventDto toDto(SigEvent e) {
        return new SigEventDto(
                e.getId(),
                e.getSourceId(),
                e.getLatitude(),
                e.getLongitude(),
                e.getElevationM(),
                e.getTrackLevel(),
                e.getZone() != null ? e.getZone().getId() : null,
                e.getZone() != null ? e.getZone().getName() : null,
                e.getOccurredAt(),
                e.getReceivedAt()
        );
    }

    public AlertDto toDto(Alert a) {
        return new AlertDto(
                a.getId(),
                a.getSeverity(),
                a.getType(),
                a.getMessage(),
                a.getLatitude(),
                a.getLongitude(),
                a.getZone() != null ? a.getZone().getId() : null,
                a.getZone() != null ? a.getZone().getName() : null,
                a.getCameraEvent() != null ? a.getCameraEvent().getId() : null,
                a.getSigEvent() != null ? a.getSigEvent().getId() : null,
                readJsonOrNull(a.getDetails()),
                a.getCreatedAt(),
                a.isDispatched(),
                a.getDispatchedAt()
        );
    }

    /**
     * Like {@link #readJson(String)} but returns {@code null} (not an empty
     * map) when the source is empty, so the JSON serialiser can omit the
     * field for alerts that have no structured details.
     */
    private Map<String, Object> readJsonOrNull(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse alert details JSON", ex);
            return null;
        }
    }

    public ZoneDto toDto(Zone z) {
        return new ZoneDto(
                z.getId(),
                z.getName(),
                z.getType(),
                z.getDescription(),
                z.getCenterLat(),
                z.getCenterLon(),
                z.getRadiusM(),
                z.getElevationM(),
                z.getHeightM(),
                z.getIsTunnel(),
                z.getIsBridge()
        );
    }

    public String writeJson(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize JSON payload", ex);
            return null;
        }
    }

    public Map<String, Object> readJson(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse JSON payload", ex);
            return Map.of();
        }
    }
}
