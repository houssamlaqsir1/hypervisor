package com.oncf.hypervisor.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for a camera installation. Used by both POST and
 * PUT on {@code /api/admin/cameras}.
 */
public record CameraRequest(
        @NotBlank String cameraId,
        @NotBlank String name,
        String site,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Double elevationM,
        /**
         * Bearing the camera faces, degrees clockwise from north (0 = north,
         * 90 = east). Surveyed at mount time like the GPS fix. Optional:
         * left null, detections are still placed at the right distance from
         * the camera, just on an arbitrary compass bearing.
         */
        @DecimalMin("0.0") @DecimalMax("360.0") Double headingDeg,
        Boolean active
) {}
