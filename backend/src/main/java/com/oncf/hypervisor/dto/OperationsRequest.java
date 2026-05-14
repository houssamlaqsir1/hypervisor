package com.oncf.hypervisor.dto;

public record OperationsRequest(
        Long zoneId,
        Double latitude,
        Double longitude
) {}
