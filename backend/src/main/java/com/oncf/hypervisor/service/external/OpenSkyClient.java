package com.oncf.hypervisor.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncf.hypervisor.config.LiveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads live aircraft states from the public OpenSky Network REST API.
 *
 * <p>The free anonymous endpoint we hit:
 * <pre>https://opensky-network.org/api/states/all?lamin=..&lomin=..&lamax=..&lomax=..</pre>
 *
 * <p>Each state in the response is a positional array. We only project the
 * fields the rest of the app cares about ({@link OpenSkyState}). When the API
 * is rate-limited or unreachable we return an empty list and log at WARN —
 * the poller treats that as "no new tracks this tick".
 */
@Component
@Slf4j
public class OpenSkyClient {

    private static final String BASE_URL = "https://opensky-network.org/api/states/all";

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public OpenSkyClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    public List<OpenSkyState> fetchStates(LiveProperties.OpenSky cfg) {
        String url = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("lamin", cfg.lamin())
                .queryParam("lomin", cfg.lomin())
                .queryParam("lamax", cfg.lamax())
                .queryParam("lomax", cfg.lomax())
                .build(true)
                .toUriString();
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = mapper.readTree(body);
            JsonNode states = root.get("states");
            if (states == null || !states.isArray() || states.isEmpty()) {
                return List.of();
            }
            List<OpenSkyState> out = new ArrayList<>(states.size());
            for (JsonNode s : states) {
                OpenSkyState parsed = parse(s);
                if (parsed != null) out.add(parsed);
            }
            return out;
        } catch (Exception ex) {
            log.warn("OpenSky fetch failed ({}): {}", url, ex.getMessage());
            return List.of();
        }
    }

    /**
     * The OpenSky state is a fixed-position array. Indices we use:
     * 0 icao24, 1 callsign, 2 origin_country, 5 longitude, 6 latitude,
     * 7 baro_altitude, 8 on_ground, 9 velocity (m/s), 10 true_track (deg).
     */
    private OpenSkyState parse(JsonNode s) {
        try {
            String icao24 = textOrNull(s, 0);
            String callsign = textOrNull(s, 1);
            String country = textOrNull(s, 2);
            Double lon = doubleOrNull(s, 5);
            Double lat = doubleOrNull(s, 6);
            if (icao24 == null || lat == null || lon == null) return null;
            Double baroAltitude = doubleOrNull(s, 7);
            boolean onGround = s.size() > 8 && s.get(8).asBoolean(false);
            Double velocity = doubleOrNull(s, 9);
            Double track = doubleOrNull(s, 10);
            return new OpenSkyState(
                    icao24.trim(),
                    callsign != null ? callsign.trim() : null,
                    country,
                    lat,
                    lon,
                    baroAltitude,
                    onGround,
                    velocity,
                    track);
        } catch (RuntimeException ex) {
            log.debug("skip malformed OpenSky state: {}", ex.getMessage());
            return null;
        }
    }

    private String textOrNull(JsonNode arr, int i) {
        if (arr == null || arr.size() <= i) return null;
        JsonNode n = arr.get(i);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private Double doubleOrNull(JsonNode arr, int i) {
        if (arr == null || arr.size() <= i) return null;
        JsonNode n = arr.get(i);
        return (n == null || n.isNull() || !n.isNumber()) ? null : n.asDouble();
    }

    public record OpenSkyState(
            String icao24,
            String callsign,
            String originCountry,
            double latitude,
            double longitude,
            Double baroAltitudeM,
            boolean onGround,
            Double velocityMs,
            Double trueTrackDeg
    ) {}
}
