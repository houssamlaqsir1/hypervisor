package com.oncf.hypervisor.dto;

/**
 * Normalized place search result (from OpenStreetMap Nominatim via server-side proxy).
 */
public record GeocodeResultDto(
        double lat,
        double lon,
        String displayName,
        Double south,
        Double north,
        Double west,
        Double east
) {}
