package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;

import java.time.Instant;

public record AlertDto(
        Long id,
        AlertSeverity severity,
        AlertType type,
        String message,
        Double latitude,
        Double longitude,
        Long zoneId,
        String zoneName,
        Long cameraEventId,
        Long sigEventId,
        Instant createdAt,
        boolean dispatched,
        Instant dispatchedAt
) {}
