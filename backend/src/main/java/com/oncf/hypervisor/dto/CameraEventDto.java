package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.CameraEventType;

import java.time.Instant;

public record CameraEventDto(
        Long id,
        String cameraId,
        CameraEventType eventType,
        String label,
        Double confidence,
        Double latitude,
        Double longitude,
        Double elevationM,
        Instant occurredAt,
        Instant receivedAt
) {}
