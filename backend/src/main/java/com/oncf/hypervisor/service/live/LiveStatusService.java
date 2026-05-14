package com.oncf.hypervisor.service.live;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks a few metrics so the UI can show whether the live ingestion
 * pipeline is actually producing events. Pure in-memory counters — they
 * reset on restart, which is fine: they exist for the operator banner,
 * not for analytics.
 *
 * <p>Per-camera heartbeats are also tracked here so a separate
 * {@code GET /api/live/cameras} endpoint can answer "which AI cameras have
 * been alive recently?" without scanning the {@code camera_events} table.
 */
@Service
public class LiveStatusService {

    /** A camera that hasn't posted in this long is reported as {@code STALE}. */
    public static final long STALE_AFTER_SECONDS = 30;

    private final AtomicReference<Instant> lastOpenSkyPollAt = new AtomicReference<>();
    private final AtomicReference<String> lastOpenSkyError = new AtomicReference<>();
    private final AtomicLong openSkyEventsTotal = new AtomicLong();
    private final AtomicReference<Instant> lastOpenSkyEventAt = new AtomicReference<>();

    private final AtomicLong webcamEventsTotal = new AtomicLong();
    private final AtomicReference<Instant> lastWebcamEventAt = new AtomicReference<>();
    private final AtomicLong gpsEventsTotal = new AtomicLong();
    private final AtomicReference<Instant> lastGpsEventAt = new AtomicReference<>();

    /**
     * External IP-camera webhooks (Xeoma, Frigate, Agent DVR, …). The brand
     * doesn't matter — anything that POSTs / GETs the {@code /api/live/ip-camera}
     * endpoint when its on-board AI fires a detection.
     */
    private final AtomicLong ipCameraEventsTotal = new AtomicLong();
    private final AtomicReference<Instant> lastIpCameraEventAt = new AtomicReference<>();
    private final AtomicReference<String> lastIpCameraSource = new AtomicReference<>();

    /** Per-camera heartbeats keyed by the {@code cameraId} on each event. */
    private final Map<String, CameraHeartbeat> cameraStats = new ConcurrentHashMap<>();

    public void recordOpenSkyPoll(int ingested, String error) {
        lastOpenSkyPollAt.set(Instant.now());
        lastOpenSkyError.set(error);
        if (ingested > 0) {
            openSkyEventsTotal.addAndGet(ingested);
            lastOpenSkyEventAt.set(Instant.now());
        }
    }

    public void recordWebcamEvent() {
        webcamEventsTotal.incrementAndGet();
        lastWebcamEventAt.set(Instant.now());
    }

    public void recordCameraEvent(String cameraId, String source) {
        if (cameraId == null || cameraId.isBlank()) return;
        cameraStats
                .computeIfAbsent(cameraId, id -> new CameraHeartbeat(id, source))
                .record(source);
    }

    public void recordGpsEvent() {
        gpsEventsTotal.incrementAndGet();
        lastGpsEventAt.set(Instant.now());
    }

    public void recordIpCameraEvent(String source) {
        ipCameraEventsTotal.incrementAndGet();
        lastIpCameraEventAt.set(Instant.now());
        if (source != null && !source.isBlank()) lastIpCameraSource.set(source);
    }

    /** Snapshot of every camera that has sent at least one event since boot. */
    public List<CameraHeartbeatSnapshot> cameraHeartbeats() {
        return cameraStats.values().stream()
                .map(CameraHeartbeat::snapshot)
                .sorted(Comparator.comparing(CameraHeartbeatSnapshot::cameraId))
                .toList();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                lastOpenSkyPollAt.get(),
                lastOpenSkyError.get(),
                openSkyEventsTotal.get(),
                lastOpenSkyEventAt.get(),
                webcamEventsTotal.get(),
                lastWebcamEventAt.get(),
                gpsEventsTotal.get(),
                lastGpsEventAt.get(),
                ipCameraEventsTotal.get(),
                lastIpCameraEventAt.get(),
                lastIpCameraSource.get()
        );
    }

    public record Snapshot(
            Instant lastOpenSkyPollAt,
            String lastOpenSkyError,
            long openSkyEventsTotal,
            Instant lastOpenSkyEventAt,
            long webcamEventsTotal,
            Instant lastWebcamEventAt,
            long gpsEventsTotal,
            Instant lastGpsEventAt,
            long ipCameraEventsTotal,
            Instant lastIpCameraEventAt,
            String lastIpCameraSource
    ) {}

    /** Immutable view of a single camera's last-seen / event-count state. */
    public record CameraHeartbeatSnapshot(
            String cameraId,
            String lastSource,
            Instant lastEventAt,
            long eventsTotal,
            boolean stale
    ) {}

    /** Mutable per-camera counter — kept inside the service. */
    private static final class CameraHeartbeat {
        private final String cameraId;
        private final AtomicLong eventsTotal = new AtomicLong();
        private final AtomicReference<Instant> lastEventAt = new AtomicReference<>();
        private final AtomicReference<String> lastSource = new AtomicReference<>();

        CameraHeartbeat(String cameraId, String initialSource) {
            this.cameraId = cameraId;
            if (initialSource != null && !initialSource.isBlank()) {
                this.lastSource.set(initialSource);
            }
        }

        void record(String source) {
            eventsTotal.incrementAndGet();
            lastEventAt.set(Instant.now());
            if (source != null && !source.isBlank()) {
                lastSource.set(source);
            }
        }

        CameraHeartbeatSnapshot snapshot() {
            Instant last = lastEventAt.get();
            boolean stale = last == null
                    || last.isBefore(Instant.now().minusSeconds(STALE_AFTER_SECONDS));
            return new CameraHeartbeatSnapshot(
                    cameraId,
                    lastSource.get(),
                    last,
                    eventsTotal.get(),
                    stale);
        }
    }
}
