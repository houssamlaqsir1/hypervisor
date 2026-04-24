package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.ZoneType;

public record ZoneDto(
        Long id,
        String name,
        ZoneType type,
        String description,
        Double centerLat,
        Double centerLon,
        Double radiusM,
        Double elevationM,
        Double heightM,
        Boolean isTunnel,
        Boolean isBridge
) {}
