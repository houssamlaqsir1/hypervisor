package com.oncf.hypervisor.dto;

public record SimulationRequest(
        Long zoneId,
        Double latitude,
        Double longitude
) {}
