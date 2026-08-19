package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.repository.CameraEventRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link UnattendedBaggageRule} — the security-relevant distinction is
 * <em>unattended</em>, not "a bag exists". A suitcase beside its owner is
 * normal; the same suitcase alone for minutes is why stations get evacuated.
 *
 * <p>Both conditions must hold: the bag persisted across the window, and
 * nobody was detected nearby during it.
 */
class UnattendedBaggageRuleTest {

    private CameraEventRepository cameraEventRepository;
    private AlertRepository alertRepository;
    private UnattendedBaggageRule rule;
    private CameraEvent triggering;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps(); // 5 min, 3 sightings
        cameraEventRepository = mock(CameraEventRepository.class);
        alertRepository = mock(AlertRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new UnattendedBaggageRule(props, cameraEventRepository, alertRepository);

        triggering = RuleTestFixtures.event(
                CameraEventType.OBJECT_DETECTED, "suitcase", 0.8, 33.6, -7.58);
        triggering.setOccurredAt(Instant.now());
    }

    /** Builds N sightings of the bag spanning the given number of minutes. */
    private void givenSightingsSpanning(int count, long minutes) {
        List<CameraEvent> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CameraEvent past = RuleTestFixtures.event(
                    CameraEventType.OBJECT_DETECTED, "suitcase", 0.8, 33.6, -7.58);
            // Oldest sits `minutes` back; the rest spread between then and now.
            past.setOccurredAt(triggering.getOccurredAt()
                    .minus(minutes * (count - i) / count, ChronoUnit.MINUTES));
            history.add(past);
        }
        when(cameraEventRepository.findTrackHistory(
                anyString(), anyString(), any(Instant.class), any(Pageable.class)))
                .thenReturn(history);
    }

    private void givenPeopleNearby(long people) {
        when(cameraEventRepository.countNearbyByLabels(
                anyDouble(), anyDouble(), anyDouble(), any(Instant.class),
                any(Collection.class), anyDouble()))
                .thenReturn(people);
    }

    private List<AlertDraft> evaluateIn(ZoneType zoneType) {
        Zone zone = RuleTestFixtures.zone(1L, "Concourse", zoneType, 33.6, -7.58, 120);
        return rule.evaluate(CorrelationContext.forCamera(triggering, List.of(zone)));
    }

    @Test
    void abandonedBagInAStationIsMedium() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(0);

        List<AlertDraft> drafts = evaluateIn(ZoneType.STATION);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.UNATTENDED_BAGGAGE);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void abandonedBagInASensitiveAreaIsHigher() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(0);

        assertThat(evaluateIn(ZoneType.RESTRICTED)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
        assertThat(evaluateIn(ZoneType.TRACK)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void anAttendedBagIsNotAnAlert() {
        // The owner is standing right there — this is just luggage.
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(4);

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
    }

    @Test
    void aBagCarriedPastTheCameraIsNotAbandoned() {
        // Plenty of sightings, but they span seconds — someone walking through.
        givenSightingsSpanning(5, 0);
        givenPeopleNearby(0);

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
    }

    @Test
    void aSingleFlickerIsNotEnough() {
        givenSightingsSpanning(1, 6);
        givenPeopleNearby(0);

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
    }

    /* ── camera-measured attendance ─────────────────────────────────── */

    /**
     * Marks the triggering event as coming from a camera that reports its
     * frame headcount, and how far the closest person was.
     *
     * @param peopleInFrame how many people the camera saw in the same frame
     * @param nearestPersonM metres to the closest one, or null when nobody is in frame
     */
    private void givenCameraMeasured(int peopleInFrame, Double nearestPersonM) {
        StringBuilder raw = new StringBuilder("{\"source\":\"yolov8_detector\",\"personCount\":")
                .append(peopleInFrame);
        if (nearestPersonM != null) raw.append(",\"nearestPersonM\":").append(nearestPersonM);
        triggering.setRawPayload(raw.append("}").toString());
    }

    @Test
    void bagAloneInFrameIsUnattended() {
        givenSightingsSpanning(5, 6);
        // Nobody in frame at all — unambiguous, and the row-count fallback is
        // mocked hostile to prove it isn't consulted.
        givenPeopleNearby(9);
        givenCameraMeasured(0, null);

        assertThat(evaluateIn(ZoneType.STATION)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void someoneStandingWithTheBagMeansItIsAttended() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(0);
        givenCameraMeasured(1, 1.2); // owner within arm's reach

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
    }

    @Test
    void aPersonAcrossTheHallIsNotAttendingTheBag() {
        // The fix that matters: someone 12 m away, elsewhere in the same
        // frame, used to count as "a person nearby" and suppress the alert.
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(3);
        givenCameraMeasured(3, 12.0);

        assertThat(evaluateIn(ZoneType.STATION)).singleElement()
                .extracting(AlertDraft::details)
                .satisfies(details -> assertThat(details)
                        .containsEntry("attendanceSource", "cameraMeasured")
                        .containsEntry("peopleInFrame", 3));
    }

    @Test
    void attendanceBoundaryIsThreeMetres() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(0);

        givenCameraMeasured(1, 3.0);
        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();

        givenCameraMeasured(1, 3.5);
        assertThat(evaluateIn(ZoneType.STATION)).hasSize(1);
    }

    @Test
    void camerasWithoutHeadcountStillUseTheOldPath() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(2);
        triggering.setRawPayload("{\"source\":\"legacy\"}");

        assertThat(evaluateIn(ZoneType.STATION)).isEmpty();
    }

    @Test
    void ignoresNonLuggageClasses() {
        givenSightingsSpanning(5, 6);
        givenPeopleNearby(0);
        CameraEvent person = RuleTestFixtures.event(
                CameraEventType.HUMAN_DETECTED, "person", 0.9, 33.6, -7.58);
        Zone zone = RuleTestFixtures.zone(1L, "Concourse", ZoneType.STATION, 33.6, -7.58, 120);

        assertThat(rule.evaluate(CorrelationContext.forCamera(person, List.of(zone)))).isEmpty();
    }
}
