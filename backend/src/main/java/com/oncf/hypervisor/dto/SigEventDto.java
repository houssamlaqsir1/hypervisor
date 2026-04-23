package com.oncf.hypervisor.dto;

import java.time.Instant;

public record SigEventDto(
        Long id,
        String sourceId,
        Double latitude,
        Double longitude,
        Long zoneId,
        String zoneName,
        Instant occurredAt,
        Instant receivedAt
) {}
