package com.oncf.hypervisor.dto;

import java.util.List;
import java.util.Map;

public record Sig3dResponseDto(
        Map<String, Object> terrain,
        GeoJsonFeatureCollectionDto rail,
        GeoJsonFeatureCollectionDto zones,
        List<AlertDto> alerts
) {
}
