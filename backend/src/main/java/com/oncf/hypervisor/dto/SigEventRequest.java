package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.TrackLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record SigEventRequest(
        @NotBlank String sourceId,
        @NotNull Double latitude,
        @NotNull Double longitude,
        Double elevationM,
        TrackLevel trackLevel,
        Long zoneId,
        Map<String, Object> metadata,
        Instant occurredAt
) {}
