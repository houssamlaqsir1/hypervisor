package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
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

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link CrowdDensityRule} — platform overcrowding is a real safety hazard
 * (people pushed toward the track edge), and the one case where sheer
 * volume of person detections is itself the danger. Threshold = 25.
 *
 * <pre>
 * | zone    | people | expected   |
 * |---------|--------|------------|
 * | STATION | 25     | MEDIUM     |
 * | STATION | 50     | HIGH       |  (2× threshold)
 * | TRACK   | 25     | HIGH       |  (crowding beside the rails)
 * | TRACK   | 40     | CRITICAL   |  (1.5× threshold)
 * | any     | &lt; 25    | (no alert) |
 * </pre>
 *
 * <p>When the detector reports {@code personCount} (how many people it saw
 * <em>simultaneously</em> in one frame) on the triggering event, that
 * headcount is authoritative and the row-counting fallback above is not
 * used — see {@link #oneLoiteringPersonIsNotACrowd()}.
 */
class CrowdDensityRuleTest {

    private CameraEventRepository cameraEventRepository;
    private AlertRepository alertRepository;
    private CrowdDensityRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps(); // crowdThreshold = 25
        cameraEventRepository = mock(CameraEventRepository.class);
        alertRepository = mock(AlertRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new CrowdDensityRule(props, cameraEventRepository, alertRepository);
    }

    private List<AlertDraft> evaluateWithCrowd(ZoneType zoneType, long people) {
        when(cameraEventRepository.countNearbyByLabels(
                anyDouble(), anyDouble(), anyDouble(), any(Instant.class),
                any(Collection.class), anyDouble()))
                .thenReturn(people);
        Zone zone = RuleTestFixtures.zone(1L, "Platform", zoneType, 33.6, -7.58, 120);
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.8, 33.6, -7.58);
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(zone)));
    }

    @Test
    void stationAtThresholdIsMedium() {
        List<AlertDraft> drafts = evaluateWithCrowd(ZoneType.STATION, 25);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.CROWD_DENSITY);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void stationAtDoubleThresholdIsHigh() {
        assertThat(evaluateWithCrowd(ZoneType.STATION, 50)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void crowdingBesideTheRailsIsTreatedMoreSeriously() {
        assertThat(evaluateWithCrowd(ZoneType.TRACK, 25)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
        assertThat(evaluateWithCrowd(ZoneType.TRACK, 40)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void normalFootfallDoesNotAlert() {
        assertThat(evaluateWithCrowd(ZoneType.STATION, 24)).isEmpty();
        assertThat(evaluateWithCrowd(ZoneType.STATION, 3)).isEmpty();
    }

    @Test
    void ignoresZonesWherePeopleDoNotGather() {
        assertThat(evaluateWithCrowd(ZoneType.RESTRICTED, 100)).isEmpty();
        assertThat(evaluateWithCrowd(ZoneType.NORMAL, 100)).isEmpty();
    }

    /* ── detector-reported headcount (personCount) ─────────────────── */

    private List<AlertDraft> evaluateWithReportedHeadcount(ZoneType zoneType, int personCount) {
        // Row-count fallback would say "way over threshold" — proves the
        // rule actually prefers personCount when it's present.
        when(cameraEventRepository.countNearbyByLabels(
                anyDouble(), anyDouble(), anyDouble(), any(Instant.class),
                any(Collection.class), anyDouble()))
                .thenReturn(999L);
        Zone zone = RuleTestFixtures.zone(1L, "Platform", zoneType, 33.6, -7.58, 120);
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.8, 33.6, -7.58);
        event.setRawPayload(
                "{\"source\":\"yolov8_detector\",\"cocoClass\":\"person\",\"personCount\":" + personCount + "}");
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(zone)));
    }

    @Test
    void oneLoiteringPersonIsNotACrowd() {
        // The bug this exists to fix: one person standing in frame gets
        // re-detected every cooldown interval, racking up dozens of rows in
        // the events table over the crowd window — but personCount says the
        // truth is "1 person in frame," so no crowd alert should fire.
        assertThat(evaluateWithReportedHeadcount(ZoneType.STATION, 1)).isEmpty();
    }

    @Test
    void reportedHeadcountAtThresholdStillAlerts() {
        assertThat(evaluateWithReportedHeadcount(ZoneType.STATION, 25)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void reportedHeadcountTakesPrecedenceOverRowCount() {
        // 3 real people in frame; the row-count fallback (mocked to 999
        // above) must be ignored once personCount is present.
        assertThat(evaluateWithReportedHeadcount(ZoneType.STATION, 3)).isEmpty();
    }
}
