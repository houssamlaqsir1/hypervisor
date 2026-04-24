package com.oncf.hypervisor.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncf.hypervisor.domain.enums.ZoneType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeoJson3dSeedLoader implements CommandLineRunner {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        importGeoJson("classpath:seed/rail_3d.geojson", ZoneType.TRACK);
        importGeoJson("classpath:seed/zones_3d.geojson", null);
    }

    private void importGeoJson(String location, ZoneType fallbackType) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("3D seed file not found (skipped): {}", location);
            return;
        }
        JsonNode root = objectMapper.readTree(resource.getInputStream());
        JsonNode features = root.path("features");
        if (!features.isArray()) return;

        int imported = 0;
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            JsonNode geometry = feature.path("geometry");
            if (geometry.isMissingNode() || geometry.isNull()) continue;

            String name = text(properties, "name", "unnamed-" + imported);
            String description = text(properties, "description", null);
            ZoneType zoneType = parseZoneType(text(properties, "type", null), fallbackType);
            Double elevationM = number(properties, "elevationM");
            Double heightM = number(properties, "heightM");
            boolean isTunnel = bool(properties, "isTunnel", false);
            boolean isBridge = bool(properties, "isBridge", false);
            String geomJson = objectMapper.writeValueAsString(geometry);

            jdbcTemplate.update("""
                    INSERT INTO zones(name, type, description, center_lat, center_lon, radius_m,
                                      elevation_m, height_m, is_tunnel, is_bridge, geom_3d)
                    VALUES (?, ?, ?, 0, 0, 1, ?, ?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                    ON CONFLICT (name) DO UPDATE SET
                        type = EXCLUDED.type,
                        description = COALESCE(EXCLUDED.description, zones.description),
                        elevation_m = COALESCE(EXCLUDED.elevation_m, zones.elevation_m),
                        height_m = COALESCE(EXCLUDED.height_m, zones.height_m),
                        is_tunnel = EXCLUDED.is_tunnel,
                        is_bridge = EXCLUDED.is_bridge,
                        geom_3d = EXCLUDED.geom_3d
                    """,
                    name, zoneType.name(), description, elevationM, heightM, isTunnel, isBridge, geomJson
            );
            imported++;
        }
        log.info("Imported {} 3D features from {}", imported, location);
    }

    private static String text(JsonNode props, String key, String fallback) {
        JsonNode n = props.path(key);
        return n.isMissingNode() || n.isNull() ? fallback : n.asText(fallback);
    }

    private static Double number(JsonNode props, String key) {
        JsonNode n = props.path(key);
        return n.isNumber() ? n.asDouble() : null;
    }

    private static boolean bool(JsonNode props, String key, boolean fallback) {
        JsonNode n = props.path(key);
        return n.isBoolean() ? n.asBoolean() : fallback;
    }

    private static ZoneType parseZoneType(String raw, ZoneType fallback) {
        if (raw == null || raw.isBlank()) return fallback != null ? fallback : ZoneType.RESTRICTED;
        try {
            return ZoneType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback != null ? fallback : ZoneType.RESTRICTED;
        }
    }
}
