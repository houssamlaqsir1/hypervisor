package com.oncf.hypervisor.service.live;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live position reports for handheld/mobile cameras (a phone, not a fixed
 * installation). A small page opened directly on the phone
 * ({@code /phone-gps} in the frontend) posts its own GPS position here
 * independently of whatever device is viewing the dashboard — so the
 * operator can watch the feed from a PC while the phone, wherever it
 * actually is, keeps reporting its own real location.
 *
 * <p>Pure in-memory, keyed by {@code cameraId}. A report older than
 * {@link #STALE_AFTER_SECONDS} is treated as absent — better to fall back
 * to "unknown" than to silently keep using a phone's last position from
 * ten minutes ago.
 */
@Service
public class LivePositionService {

    public static final long STALE_AFTER_SECONDS = 30;

    public record Position(double latitude, double longitude, Double elevationM, Instant reportedAt) {}

    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    public void report(String cameraId, double latitude, double longitude, Double elevationM) {
        positions.put(cameraId, new Position(latitude, longitude, elevationM, Instant.now()));
    }

    /** The camera's most recent position, if it reported one recently enough to trust. */
    public Optional<Position> current(String cameraId) {
        Position p = positions.get(cameraId);
        if (p == null) return Optional.empty();
        if (p.reportedAt().isBefore(Instant.now().minusSeconds(STALE_AFTER_SECONDS))) {
            return Optional.empty();
        }
        return Optional.of(p);
    }
}
