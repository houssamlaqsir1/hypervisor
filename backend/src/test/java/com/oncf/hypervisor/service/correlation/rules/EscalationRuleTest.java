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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EscalationRule} — repeated activity only escalates where being
 * present is already the danger. Threshold is 4 significant events.
 *
 * <pre>
 * | zone              | count | expected   |
 * |-------------------|-------|------------|
 * | TRACK/RESTRICTED  | 4     | HIGH       |
 * | TRACK/RESTRICTED  | ≥ 6   | CRITICAL   |  (1.5× threshold)
 * | STATION/NORMAL    | any   | (no alert) |
 * | any               | &lt; 4    | (no alert) |
 * </pre>
 */
class EscalationRuleTest {

    private CameraEventRepository cameraEventRepository;
    private AlertRepository alertRepository;
    private EscalationRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps(); // threshold = 4
        cameraEventRepository = mock(CameraEventRepository.class);
        alertRepository = mock(AlertRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new EscalationRule(props, cameraEventRepository, alertRepository);
    }

    private List<AlertDraft> evaluateWithCount(ZoneType zoneType, long significantCount) {
        when(cameraEventRepository.countSignificantNearby(
                anyDouble(), anyDouble(), anyDouble(), any(Instant.class), anyDouble()))
                .thenReturn(significantCount);
        Zone zone = RuleTestFixtures.zone(1L, "Zone", zoneType, 34.0, -6.85, 100);
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.8, 34.0, -6.85);
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(zone)));
    }

    @Test
    void trackAtThresholdIsHigh() {
        List<AlertDraft> drafts = evaluateWithCount(ZoneType.TRACK, 4);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.ESCALATION);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void trackAtOneAndAHalfThresholdIsCritical() {
        assertThat(evaluateWithCount(ZoneType.TRACK, 6)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void restrictedEscalatesToo() {
        assertThat(evaluateWithCount(ZoneType.RESTRICTED, 4)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void stationNeverEscalatesRegardlessOfCount() {
        // Stations are supposed to be busy — volume alone is never an incident.
        assertThat(evaluateWithCount(ZoneType.STATION, 50)).isEmpty();
        assertThat(evaluateWithCount(ZoneType.NORMAL, 50)).isEmpty();
    }

    @Test
    void belowThresholdDoesNotFire() {
        assertThat(evaluateWithCount(ZoneType.TRACK, 3)).isEmpty();
    }

    @Test
    void respectsCooldown() {
        when(alertRepository.existsRecentByCameraLabelZone(
                eq(AlertType.ESCALATION), any(), any(), any(), any(Instant.class)))
                .thenReturn(true);
        assertThat(evaluateWithCount(ZoneType.TRACK, 10)).isEmpty();
    }
}
