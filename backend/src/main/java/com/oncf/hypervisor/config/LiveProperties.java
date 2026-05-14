package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the live ingestion pipeline.
 *
 * <p>Two real data sources can be plugged in:
 * <ul>
 *   <li>Backend pull: {@link OpenSky} polls the public OpenSky Network REST API
 *       and turns each state vector into a SIG event.</li>
 *   <li>Frontend push: the Live Watch page streams real webcam detections
 *       (camera events) and real geolocation (SIG events). No config needed
 *       there beyond the existing ingest endpoints.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "hypervisor.live")
public record LiveProperties(
        OpenSky opensky
) {
    public LiveProperties {
        if (opensky == null) opensky = new OpenSky(true, 15, 33.0, -8.5, 34.2, -7.0, 8, 60, true);
    }

    /**
     * @param enabled               feature flag for the scheduled poller
     * @param pollIntervalSeconds   how often to hit OpenSky (their free tier
     *                              is fine at ~10s, we default to 15)
     * @param lamin                 bounding box minimum latitude
     * @param lomin                 bounding box minimum longitude
     * @param lamax                 bounding box maximum latitude
     * @param lomax                 bounding box maximum longitude
     * @param maxEventsPerPoll      cap how many states we forward per tick
     *                              so a noisy sky doesn't drown the DB
     * @param dedupeWindowSeconds   ignore the same icao24 if seen within
     *                              this many seconds
     * @param projectToGround       if true, set elevationM = 0 so aircraft
     *                              tracks correlate with ground-level zones
     *                              (the demo wants fusion alerts to fire,
     *                              not aircraft hovering 10km up)
     */
    public record OpenSky(
            boolean enabled,
            int pollIntervalSeconds,
            double lamin,
            double lomin,
            double lamax,
            double lomax,
            int maxEventsPerPoll,
            int dedupeWindowSeconds,
            boolean projectToGround
    ) {
        public OpenSky {
            if (pollIntervalSeconds < 5) pollIntervalSeconds = 5;
            if (maxEventsPerPoll <= 0) maxEventsPerPoll = 8;
            if (dedupeWindowSeconds < 0) dedupeWindowSeconds = 60;
        }
    }
}
