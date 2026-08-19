package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.ZoneType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Create/update payload for a surveillance zone. The 3D attributes
 * (elevation, height, tunnel/bridge) are optional — a zone created with
 * just a center and radius still matches events via the 2D fallback in the
 * correlation engine.
 */
public record ZoneRequest(
        @NotBlank String name,
        @NotNull ZoneType type,
        String description,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double centerLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double centerLon,
        @NotNull @Positive Double radiusM,
        Double elevationM,
        Double heightM,
        Boolean isTunnel,
        Boolean isBridge
) {}
