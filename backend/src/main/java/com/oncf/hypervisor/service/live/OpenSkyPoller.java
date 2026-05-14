package com.oncf.hypervisor.service.live;

import com.oncf.hypervisor.config.LiveProperties;
import com.oncf.hypervisor.domain.enums.TrackLevel;
import com.oncf.hypervisor.dto.SigEventRequest;
import com.oncf.hypervisor.service.SigEventService;
import com.oncf.hypervisor.service.external.OpenSkyClient;
import com.oncf.hypervisor.service.external.OpenSkyClient.OpenSkyState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Always-on bridge between the public OpenSky Network feed and the SIG
 * ingestion pipeline. Each tick:
 * <ol>
 *   <li>asks {@link OpenSkyClient} for aircraft states inside the configured bbox,</li>
 *   <li>filters out states we already forwarded inside the dedupe window,</li>
 *   <li>caps the batch size, then,</li>
 *   <li>calls {@link SigEventService#ingest} for each — which runs the same
 *       correlation engine the manual / drill paths use, so fusion alerts
 *       fire automatically when a real camera detection lines up.</li>
 * </ol>
 *
 * <p>Disabled by setting {@code hypervisor.live.opensky.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSkyPoller {

    private final LiveProperties props;
    private final OpenSkyClient client;
    private final SigEventService sigEventService;
    private final LiveStatusService status;

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    @PostConstruct
    void announce() {
        LiveProperties.OpenSky cfg = props.opensky();
        if (cfg.enabled()) {
            log.info("OpenSky live poller enabled (interval={}s, bbox=[{},{} -> {},{}], cap={}/poll)",
                    cfg.pollIntervalSeconds(), cfg.lamin(), cfg.lomin(), cfg.lamax(), cfg.lomax(),
                    cfg.maxEventsPerPoll());
        } else {
            log.info("OpenSky live poller disabled (hypervisor.live.opensky.enabled=false)");
        }
    }

    /**
     * Fixed-rate scheduling with the value pulled from properties at startup.
     * We can't bind a record field directly with SpEL like the YAML notation,
     * so we use {@code fixedDelayString} with a property placeholder that
     * mirrors {@code LiveProperties.OpenSky.pollIntervalSeconds}.
     */
    @Scheduled(
            fixedDelayString = "${hypervisor.live.opensky.poll-interval-seconds:15}000",
            initialDelayString = "5000")
    public void poll() {
        LiveProperties.OpenSky cfg = props.opensky();
        if (!cfg.enabled()) return;

        Instant tickStart = Instant.now();
        List<OpenSkyState> states;
        try {
            states = client.fetchStates(cfg);
        } catch (RuntimeException ex) {
            log.warn("OpenSky poll threw: {}", ex.getMessage());
            status.recordOpenSkyPoll(0, ex.getMessage());
            return;
        }
        if (states.isEmpty()) {
            status.recordOpenSkyPoll(0, null);
            return;
        }

        List<OpenSkyState> fresh = filterFresh(states, cfg, tickStart);
        if (fresh.isEmpty()) {
            status.recordOpenSkyPoll(0, null);
            return;
        }

        int ingested = 0;
        for (OpenSkyState st : fresh) {
            try {
                sigEventService.ingest(toRequest(st, cfg));
                ingested++;
            } catch (RuntimeException ex) {
                log.warn("OpenSky -> SIG ingest failed for {}: {}", st.icao24(), ex.getMessage());
            }
        }
        status.recordOpenSkyPoll(ingested, null);
        log.debug("OpenSky tick: fetched={}, fresh={}, ingested={}", states.size(), fresh.size(), ingested);
    }

    private List<OpenSkyState> filterFresh(List<OpenSkyState> states,
                                           LiveProperties.OpenSky cfg,
                                           Instant tickStart) {
        // LinkedHashMap to preserve order while we cap the batch.
        Map<String, OpenSkyState> picked = new LinkedHashMap<>();
        for (OpenSkyState st : states) {
            if (picked.size() >= cfg.maxEventsPerPoll()) break;
            Instant prev = lastSeen.get(st.icao24());
            if (prev != null
                    && ChronoUnit.SECONDS.between(prev, tickStart) < cfg.dedupeWindowSeconds()) {
                continue;
            }
            picked.put(st.icao24(), st);
        }
        // Mark them as seen *after* selection so we don't keep deduping
        // the same handful of aircraft forever.
        Instant now = Instant.now();
        for (String icao : picked.keySet()) lastSeen.put(icao, now);
        purgeStale(now);
        return List.copyOf(picked.values());
    }

    /** Stop the dedupe map from growing unboundedly during long uptimes. */
    private void purgeStale(Instant now) {
        if (lastSeen.size() < 2_000) return;
        lastSeen.entrySet().removeIf(e ->
                ChronoUnit.MINUTES.between(e.getValue(), now) > 30);
    }

    private SigEventRequest toRequest(OpenSkyState st, LiveProperties.OpenSky cfg) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "opensky");
        meta.put("icao24", st.icao24());
        if (st.callsign() != null && !st.callsign().isBlank()) meta.put("callsign", st.callsign());
        if (st.originCountry() != null) meta.put("origin_country", st.originCountry());
        if (st.velocityMs() != null) meta.put("velocity_ms", st.velocityMs());
        if (st.trueTrackDeg() != null) meta.put("heading_deg", st.trueTrackDeg());
        if (st.baroAltitudeM() != null) meta.put("baro_altitude_m", st.baroAltitudeM());
        meta.put("on_ground", st.onGround());
        meta.put("projected_to_ground", cfg.projectToGround());

        double elevationM = cfg.projectToGround() ? 0.0
                : (st.baroAltitudeM() != null ? st.baroAltitudeM() : 0.0);
        TrackLevel level = st.onGround() ? TrackLevel.GROUND : TrackLevel.GROUND;

        String sourceId = "OPENSKY-" + st.icao24().toUpperCase();
        return new SigEventRequest(
                sourceId,
                st.latitude(),
                st.longitude(),
                elevationM,
                level,
                null,
                meta,
                Instant.now()
        );
    }
}
