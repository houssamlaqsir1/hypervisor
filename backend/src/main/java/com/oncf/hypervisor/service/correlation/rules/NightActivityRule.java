package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy;
import com.oncf.hypervisor.service.correlation.CameraClassTaxonomy.Category;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import com.oncf.hypervisor.service.correlation.CorrelationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule 9: activity during closed hours.
 *
 * <p>Context changes what a detection means. A person on a platform at
 * 17:00 is a passenger; the same person at 03:00, when no service runs and
 * the site should be empty, is worth an operator's attention — this is the
 * window in which vandalism, cable theft (a real and expensive problem on
 * rail networks) and trespass actually happen.
 *
 * <p>This rule deliberately raises the floor rather than duplicating the
 * daytime rules: it only fires for zone types where daytime presence would
 * be unremarkable (STATION / NORMAL). TRACK and RESTRICTED zones are
 * already handled at HIGH/CRITICAL around the clock by
 * {@link ObjectOnTrackRule} and {@link IntrusionInRestrictedZoneRule}, so
 * re-flagging them here would just double the noise.
 *
 * <p>Hours are interpreted in ONCF's local timezone, not the server's — a
 * server running in UTC must not think 23:00 Casablanca is still daytime.
 */
@Component
@Order(34)
@RequiredArgsConstructor
@Slf4j
public class NightActivityRule implements CorrelationRule {

    private static final double MIN_CONFIDENCE = 0.6;

    private final CorrelationProperties props;
    private final AlertRepository alertRepository;

    @Override
    public List<AlertDraft> evaluate(CorrelationContext ctx) {
        CameraEvent e = ctx.cameraEvent();
        if (e == null || e.getOccurredAt() == null) return List.of();
        if (e.getConfidence() == null || e.getConfidence() < MIN_CONFIDENCE) return List.of();

        Category category = CameraClassTaxonomy.classify(e.getLabel());
        if (category != Category.PERSON && category != Category.VEHICLE) return List.of();
        if (ctx.matchingZones().isEmpty()) return List.of();
        if (!isDuringClosedHours(e.getOccurredAt())) return List.of();

        // Only zones whose daytime activity is normal — the dangerous zones
        // are already covered at high severity by the round-the-clock rules.
        Zone zone = ctx.matchingZones().stream()
                .filter(z -> z.getType() == ZoneType.STATION || z.getType() == ZoneType.NORMAL)
                .min(Comparator.comparingInt(z -> z.getType() == ZoneType.STATION ? 0 : 1))
                .orElse(null);
        if (zone == null) return List.of();

        Instant cooldownSince = Instant.now().minusSeconds(props.cooldownNightSec());
        if (alertRepository.existsRecentByCameraLabelZone(
                AlertType.NIGHT_ACTIVITY, e.getCameraId(), zone.getId(), e.getLabel(), cooldownSince)) {
            return List.of();
        }

        // A vehicle somewhere it has no business being at night is worse
        // than a person who may simply be a stranded passenger or staff.
        AlertSeverity severity = category == Category.VEHICLE
                ? AlertSeverity.HIGH
                : AlertSeverity.MEDIUM;

        String display = CameraClassTaxonomy.display(e);
        String localTime = localTimeOf(e.getOccurredAt());
        String msg = String.format(
                Locale.ROOT,
                "Out-of-hours activity — %s in '%s' at %s, site should be closed (cam %s)",
                display, zone.getName(), localTime, e.getCameraId());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rule", "nightActivity");
        details.put("category", category.name());
        details.put("classLabel", e.getLabel());
        details.put("displayName", display);
        details.put("cameraId", e.getCameraId());
        details.put("localTime", localTime);
        details.put("closedHours", props.nightStartHour() + ":00–" + props.nightEndHour() + ":00");
        details.put("timezone", props.nightZoneId());
        details.put("zoneName", zone.getName());
        details.put("zoneType", zone.getType().name());

        return List.of(AlertDraft.builder()
                .severity(severity)
                .type(AlertType.NIGHT_ACTIVITY)
                .message(msg)
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .zone(zone)
                .cameraEvent(e)
                .details(details)
                .build());
    }

    /**
     * True when the instant falls inside the configured closed window, in
     * ONCF's local time. Handles the window wrapping past midnight
     * (23:00 → 05:00), which the naive {@code start <= h && h < end} check
     * gets wrong.
     */
    private boolean isDuringClosedHours(Instant occurredAt) {
        int hour = ZonedDateTime.ofInstant(occurredAt, resolveZone()).getHour();
        int start = props.nightStartHour();
        int end = props.nightEndHour();
        return start <= end ? (hour >= start && hour < end) : (hour >= start || hour < end);
    }

    private String localTimeOf(Instant occurredAt) {
        ZonedDateTime local = ZonedDateTime.ofInstant(occurredAt, resolveZone());
        return String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute());
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(props.nightZoneId());
        } catch (Exception ex) {
            log.warn("Invalid night-zone-id '{}', falling back to UTC", props.nightZoneId());
            return ZoneId.of("UTC");
        }
    }
}
