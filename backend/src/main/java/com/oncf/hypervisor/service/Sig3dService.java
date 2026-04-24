package com.oncf.hypervisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.GeoJsonFeatureCollectionDto;
import com.oncf.hypervisor.dto.Sig3dResponseDto;
import com.oncf.hypervisor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Sig3dService {

    private final JdbcTemplate jdbcTemplate;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final com.oncf.hypervisor.mapper.HypervisorMapper mapper;

    @Transactional(readOnly = true)
    public Sig3dResponseDto getScene() {
        List<Map<String, Object>> railFeatures = queryFeatures("""
                SELECT id, name, type::text, description, elevation_m, height_m, is_tunnel, is_bridge,
                       ST_AsGeoJSON(ST_Force3D(ST_SimplifyPreserveTopology(geom_3d, 0.00001))) AS geom
                FROM zones
                WHERE type = 'TRACK' AND geom_3d IS NOT NULL
                """);
        List<Map<String, Object>> zoneFeatures = queryFeatures("""
                SELECT id, name, type::text, description, elevation_m, height_m, is_tunnel, is_bridge,
                       ST_AsGeoJSON(geom_3d) AS geom
                FROM zones
                WHERE geom_3d IS NOT NULL
                """);

        List<AlertDto> alerts = alertRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(mapper::toDto)
                .toList();

        Map<String, Object> terrain = Map.of(
                "provider", "cesium-ion",
                "suggestedAsset", "world-terrain"
        );

        return new Sig3dResponseDto(
                terrain,
                GeoJsonFeatureCollectionDto.of(railFeatures),
                GeoJsonFeatureCollectionDto.of(zoneFeatures),
                alerts
        );
    }

    private List<Map<String, Object>> queryFeatures(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String geometry = rs.getString("geom");
            Map<String, Object> geometryNode;
            try {
                geometryNode = objectMapper.readValue(geometry, new TypeReference<>() {});
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to parse geometry JSON", ex);
            }
            Map<String, Object> props = new HashMap<>();
            props.put("id", rs.getLong("id"));
            props.put("name", rs.getString("name"));
            props.put("type", rs.getString("type"));
            props.put("description", rs.getString("description"));
            props.put("elevationM", rs.getObject("elevation_m"));
            props.put("heightM", rs.getObject("height_m"));
            props.put("isTunnel", rs.getObject("is_tunnel"));
            props.put("isBridge", rs.getObject("is_bridge"));

            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", props);
            feature.put("geometry", geometryNode);
            return feature;
        });
    }
}
