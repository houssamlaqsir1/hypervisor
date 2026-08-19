package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.behavior.LoiteringModelProvider;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LoiteringBehaviorRule} scores a <em>track</em> — a position
 * history — rather than a single frame, so these tests are mostly about
 * what happens when that history isn't really a track.
 *
 * <p>Every feature the model reads (wander radius, path length, net
 * displacement, average speed) is a difference between successive
 * positions. If all the positions are identical, those features are
 * structurally zero no matter what the person did, and any probability the
 * model returns describes the camera's mounting position rather than
 * anyone's behaviour. That is exactly the state the system was in before
 * detections carried their own coordinates, and the guard asserted here is
 * what stops it being reported as a confident verdict.
 */
class LoiteringBehaviorRuleTest {

    private static final double CAM_LAT = 33.6;
    private static final double CAM_LON = -7.58;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private CameraEventRepository cameraEventRepository;
    private AlertRepository alertRepository;
    private LoiteringModelProvider modelProvider;
    private LoiteringBehaviorRule rule;
    private CameraEvent triggering;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps(); // 4 events, 90s dwell
        cameraEventRepository = mock(CameraEventRepository.class);
        alertRepository = mock(AlertRepository.class);
        modelProvider = mock(LoiteringModelProvider.class);

        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        // A model that is certain everything is loitering, so anything that
        // stays silent below is the rule's own doing, not the model's.
        when(modelProvider.predict(any())).thenReturn(0.99);
        when(modelProvider.getThreshold()).thenReturn(0.5);
        when(modelProvider.getVersion()).thenReturn("test-model");

        rule = new LoiteringBehaviorRule(props, cameraEventRepository, alertRepository, modelProvider);
        triggering = RuleTestFixtures.event(
                CameraEventType.HUMAN_DETECTED, "person", 0.9, CAM_LAT, CAM_LON);
        triggering.setOccurredAt(Instant.now());
    }

    /**
     * Builds a history of {@code count} sightings spanning {@code minutes},
     * each displaced {@code metresApart} from the last — 0 reproduces the old
     * behaviour where every event carried the camera's own coordinate.
     *
     * <p>Ordered newest-first, matching what {@code findTrackHistory} returns
     * (the rule reverses it into chronological order itself).
     */
    private void givenTrack(int count, long minutes, double metresApart) {
        List<CameraEvent> newestFirst = new ArrayList<>();
        int steps = Math.max(1, count - 1);
        for (int i = 0; i < count; i++) {
            CameraEvent past = RuleTestFixtures.event(
                    CameraEventType.HUMAN_DETECTED, "person",
                    0.9, CAM_LAT + (i * metresApart) / METERS_PER_DEG_LAT, CAM_LON);
            // i = 0 is the most recent; the last one sits `minutes` back.
            past.setOccurredAt(triggering.getOccurredAt()
                    .minus(minutes * i / steps, ChronoUnit.MINUTES));
            newestFirst.add(past);
        }
        when(cameraEventRepository.findTrackHistory(
                anyString(), anyString(), any(Instant.class), any(Pageable.class)))
                .thenReturn(newestFirst);
    }

    private List<AlertDraft> evaluateIn(ZoneType zoneType) {
        Zone zone = RuleTestFixtures.zone(1L, "Tracks North", zoneType, CAM_LAT, CAM_LON, 120);
        return rule.evaluate(CorrelationContext.forCamera(triggering, List.of(zone)));
    }

    @Test
    void scoresATrackThatActuallyMoved() {
        givenTrack(8, 5, 3.0); // pacing a few metres between sightings

        assertThat(evaluateIn(ZoneType.TRACK)).singleElement()
                .extracting(AlertDraft::type)
                .isEqualTo(AlertType.LOITERING);
    }

    @Test
    void refusesToScoreAHistoryOfIdenticalCoordinates() {
        // Every point on the camera's own position — no movement information
        // exists, so there is nothing to be confident about. The model would
        // still return 0.99 here; the rule must not ask it.
        givenTrack(8, 5, 0.0);

        assertThat(evaluateIn(ZoneType.TRACK)).isEmpty();
    }

    @Test
    void aTrackWithRealMovementReportsRealFeatures() {
        givenTrack(8, 5, 3.0);
        var details = evaluateIn(ZoneType.TRACK).get(0).details();

        // The numbers an examiner would ask about have to be non-zero.
        assertThat((Double) details.get("maxRadiusM")).isGreaterThan(0.0);
        assertThat((Double) details.get("pathLengthM")).isGreaterThan(0.0);
    }

    @Test
    void staysSilentWhereLingeringIsNormal() {
        // Standing still in a concourse is not dangerous however long it lasts.
        givenTrack(8, 5, 3.0);

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
        assertThat(evaluateIn(ZoneType.NORMAL)).isEmpty();
    }

    @Test
    void needsEnoughSightingsToBeATrackAtAll() {
        givenTrack(2, 5, 3.0); // below loiteringMinEvents = 4

        assertThat(evaluateIn(ZoneType.TRACK)).isEmpty();
    }

    @Test
    void needsEnoughDwellTime() {
        givenTrack(8, 0, 3.0); // all within the same second

        assertThat(evaluateIn(ZoneType.TRACK)).isEmpty();
    }

    @Test
    void respectsTheModelThreshold() {
        when(modelProvider.predict(any())).thenReturn(0.10);
        givenTrack(8, 5, 3.0);

        assertThat(evaluateIn(ZoneType.TRACK)).isEmpty();
    }
}
