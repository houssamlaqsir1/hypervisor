package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.Zone;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.domain.enums.ZoneType;
import com.oncf.hypervisor.repository.AlertRepository;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FallDetectionRule} is a real-hazard signal, not a time-based proxy:
 * a detected fall is urgent everywhere (HIGH), and CRITICAL where nobody
 * should be to help quickly (TRACK / RESTRICTED).
 */
class FallDetectionRuleTest {

    private AlertRepository alertRepository;
    private FallDetectionRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps();
        alertRepository = mock(AlertRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new FallDetectionRule(props, alertRepository);
    }

    private List<AlertDraft> fallIn(ZoneType zoneType) {
        Zone zone = RuleTestFixtures.zone(1L, "Zone", zoneType, 34.0, -6.85, 100);
        var event = RuleTestFixtures.event(CameraEventType.ANOMALY, "person_fallen", 0.8, 34.0, -6.85);
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(zone)));
    }

    @Test
    void fallInStationIsHigh() {
        List<AlertDraft> drafts = fallIn(ZoneType.STATION);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.FALL_DETECTED);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void fallOnTrackIsCritical() {
        assertThat(fallIn(ZoneType.TRACK)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void fallInRestrictedIsCritical() {
        assertThat(fallIn(ZoneType.RESTRICTED)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void fallWithNoMatchingZoneStillFiresHigh() {
        var event = RuleTestFixtures.event(CameraEventType.ANOMALY, "person_fallen", 0.8, 34.0, -6.85);
        List<AlertDraft> drafts = rule.evaluate(CorrelationContext.forCamera(event, List.of()));
        assertThat(drafts).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void ignoresOrdinaryDetections() {
        // A normal person detection is not a fall.
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.9, 34.0, -6.85);
        Zone zone = RuleTestFixtures.zone(1L, "Zone", ZoneType.TRACK, 34.0, -6.85, 100);
        assertThat(rule.evaluate(CorrelationContext.forCamera(event, List.of(zone)))).isEmpty();
    }
}
