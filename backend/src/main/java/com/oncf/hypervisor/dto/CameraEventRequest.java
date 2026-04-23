package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.CameraEventType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

public record CameraEventRequest(
        @NotBlank String cameraId,
        @NotNull CameraEventType eventType,
        String label,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        @NotNull Double latitude,
        @NotNull Double longitude,
        Instant occurredAt,
        Map<String, Object> rawPayload
) {}
