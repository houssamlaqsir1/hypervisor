package com.oncf.hypervisor.dto;

import java.util.List;
import java.util.Map;

public record GeoJsonFeatureCollectionDto(
        String type,
        List<Map<String, Object>> features
) {
    public static GeoJsonFeatureCollectionDto of(List<Map<String, Object>> features) {
        return new GeoJsonFeatureCollectionDto("FeatureCollection", features);
    }
}
