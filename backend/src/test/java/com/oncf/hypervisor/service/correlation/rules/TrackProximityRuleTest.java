package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.ZoneRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TrackProximityRule} — someone near a live track but not on it.
 *
 * <p>The rule has two ways of measuring "near", and they are not equally
 * good. When the camera has a track footprint configured, the detector
 * measures the gap from the person's feet to the rails in the image and
 * reports {@code trackDistanceM}; the rule then grades in real metres:
 *
 * <pre>
 * | gap        | severity   |
 * |------------|------------|
 * | ≤ 5 m      | HIGH       |  (swept envelope of a passing train)
 * | 5–15 m     | MEDIUM     |  (close enough to tell an operator)
 * | &gt; 15 m     | (no alert) |  (standing near a railway, as people do)
 * </pre>
 *
 * <p>Without that measurement it falls back to comparing coordinates
 * against the track zone's circle — coarser, and the reason the measured
 * path exists at all.
 */
class TrackProximityRuleTest {

    private AlertRepository alertRepository;
    private ZoneRepository zoneRepository;
    private TrackProximityRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps();
        alertRepository = mock(AlertRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new TrackProximityRule(props, zoneRepository, alertRepository);
    }

    /** A person inside a TRACK zone whose camera measured the gap to the rails. */
    private List<AlertDraft> measured(String label, Double gapM, Double overlap) {
        Zone track = RuleTestFixtures.zone(1L, "Tracks North", ZoneType.TRACK, 33.6, -7.58, 80);
        CameraEvent event = RuleTestFixtures.event(
                CameraEventType.HUMAN_DETECTED, label, 0.9, 33.6, -7.58);

        StringBuilder raw = new StringBuilder("{\"source\":\"yolov8_detector\"");
        if (gapM != null) raw.append(",\"trackDistanceM\":").append(gapM);
        if (overlap != null) raw.append(",\"trackOverlap\":").append(overlap);
        event.setRawPayload(raw.append("}").toString());

        return rule.evaluate(CorrelationContext.forCamera(event, List.of(track)));
    }

    @ParameterizedTest(name = "person {0}m from the rails → {1}")
    @CsvSource({
            "0.5,  HIGH",
            "3.0,  HIGH",
            "5.0,  HIGH",
            "5.1,  MEDIUM",
            "10.0, MEDIUM",
            "15.0, MEDIUM",
    })
    void gradesByMeasuredDistanceToTheRails(double gapM, AlertSeverity expected) {
        assertThat(measured("person", gapM, 0.0)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(expected);
    }

    @Test
    void beyondTheWarningCorridorNothingIsRaised() {
        // Standing 20 m from a railway is not an incident.
        assertThat(measured("person", 20.0, 0.0)).isEmpty();
        assertThat(measured("person", 150.0, 0.0)).isEmpty();
    }

    @Test
    void distanceVariesSoTheRuleIsNotAConstant() {
        // The degenerate case this replaced: with every event stamped at the
        // camera's fixed position the computed distance never changed, so the
        // rule always fired or never did regardless of where anyone stood.
        AlertSeverity close = measured("person", 2.0, 0.0).get(0).severity();
        AlertSeverity middling = measured("person", 12.0, 0.0).get(0).severity();
        List<AlertDraft> far = measured("person", 40.0, 0.0);

        assertThat(close).isEqualTo(AlertSeverity.HIGH);
        assertThat(middling).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(far).isEmpty();
    }

    @Test
    void onTheRailsIsLeftToObjectOnTrackRule() {
        // Overlapping the rails is an intrusion, not an approach — one
        // incident must not produce two alerts from two rules.
        assertThat(measured("person", 0.0, 0.6)).isEmpty();
        assertThat(measured("person", 0.0, 0.10)).isEmpty();
    }

    @Test
    void animalsAreCoveredTooButVehiclesAreNot() {
        // A cow wandering towards the rails is the same class of hazard as a
        // person. A car parked near them is not this rule's business.
        assertThat(measured("cow", 3.0, 0.0)).hasSize(1);
        assertThat(measured("car", 3.0, 0.0)).isEmpty();
    }

    @Test
    void reportsTheMeasuredDistanceForTheOperator() {
        AlertDraft draft = measured("person", 2.5, 0.0).get(0);

        assertThat(draft.type()).isEqualTo(AlertType.TRACK_PROXIMITY);
        assertThat(draft.message()).contains("2.5m from live track");
        assertThat(draft.details()).containsEntry("distanceSource", "cameraMeasured");
    }

    @Test
    void respectsTheCooldown() {
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(true);

        assertThat(measured("person", 2.0, 0.0)).isEmpty();
    }

    @Test
    void withoutAMeasurementItFallsBackToZoneGeometry() {
        // No trackDistanceM in the payload: the old coordinate-based path runs.
        // The event sits inside the TRACK zone, which that path defers on.
        assertThat(measured("person", null, null)).isEmpty();
    }
}
