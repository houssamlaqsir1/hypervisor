package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.TrackLevel;

import java.time.Instant;

public record SigEventDto(
        Long id,
        String sourceId,
        Double latitude,
        Double longitude,
        Double elevationM,
        TrackLevel trackLevel,
        Long zoneId,
        String zoneName,
        Instant occurredAt,
        Instant receivedAt
) {}
