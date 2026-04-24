package com.oncf.hypervisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncf.hypervisor.dto.GeocodeResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Proxies geocoding to OpenStreetMap Nominatim with a proper User-Agent (required by their
 * usage policy). Keeps the browser from calling Nominatim directly.
 */
@Service
@Slf4j
public class GeocodeService {

    private static final String USER_AGENT = "ONCF-Hypervisor-PFE/1.0 (student project; contact via institution)";

    private final ObjectMapper objectMapper;
    private final RestClient nominatim;

    public GeocodeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.nominatim = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<GeocodeResultDto> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        try {
            String body = nominatim.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", q)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", "6")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                return List.of();
            }
            List<GeocodeResultDto> out = new ArrayList<>();
            for (JsonNode n : root) {
                double lat = n.path("lat").asDouble(Double.NaN);
                double lon = n.path("lon").asDouble(Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }
                String name = n.path("display_name").asText("");
                Double south = null, north = null, west = null, east = null;
                JsonNode bbox = n.get("boundingbox");
                if (bbox != null && bbox.isArray() && bbox.size() >= 4) {
                    south = bbox.get(0).asDouble();
                    north = bbox.get(1).asDouble();
                    west = bbox.get(2).asDouble();
                    east = bbox.get(3).asDouble();
                }
                out.add(new GeocodeResultDto(lat, lon, name, south, north, west, east));
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("Geocode upstream request failed: {}", ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("Geocode parse failed", ex);
            return List.of();
        }
    }
}
