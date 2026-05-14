package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;

import java.time.Instant;
import java.util.Map;

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
        /**
         * Structured "why" for the alert. For FUSION alerts this exposes the
         * fusion score and the metric distance / time delta the score was
         * computed from; the UI uses it to render correlation chips.
         */
        Map<String, Object> details,
        Instant createdAt,
        boolean dispatched,
        Instant dispatchedAt
) {}
