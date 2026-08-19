package com.oncf.hypervisor.dto;

import java.time.Instant;

/** Public view of a registered camera installation. */
public record CameraDto(
        Long id,
        String cameraId,
        String name,
        String site,
        Double latitude,
        Double longitude,
        Double elevationM,
        /** Bearing the camera faces, degrees clockwise from north; null = unsurveyed. */
        Double headingDeg,
        boolean active,
        Instant createdAt
) {}
