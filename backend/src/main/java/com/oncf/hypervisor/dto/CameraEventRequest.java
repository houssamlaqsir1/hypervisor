package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.CameraEventType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;

/**
 * {@code latitude}/{@code longitude} are optional: when {@code cameraId}
 * matches a registered {@link com.oncf.hypervisor.domain.Camera}, the
 * server resolves the event's location from that camera's fixed
 * installation record and ignores whatever the client sent — the same way
 * a real CCTV camera's position is surveyed once at install time, not
 * re-declared on every frame. They're only used as a fallback for
 * unregistered/ad-hoc cameras (e.g. scripted test scenarios).
 */
public record CameraEventRequest(
        @NotBlank String cameraId,
        @NotNull CameraEventType eventType,
        String label,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        Double latitude,
        Double longitude,
        Double elevationM,
        Instant occurredAt,
        Map<String, Object> rawPayload
) {}
