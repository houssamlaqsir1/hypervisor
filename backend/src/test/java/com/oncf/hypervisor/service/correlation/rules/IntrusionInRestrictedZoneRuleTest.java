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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Severity contract for {@link IntrusionInRestrictedZoneRule}: a person in a
 * RESTRICTED zone is graded on detection confidence.
 *
 * <pre>
 * | confidence | expected severity |
 * |------------|-------------------|
 * | &lt; 0.70     | (no alert)        |
 * | 0.70–0.79  | MEDIUM            |
 * | 0.80–0.89  | HIGH              |
 * | ≥ 0.90     | CRITICAL          |
 * </pre>
 */
class IntrusionInRestrictedZoneRuleTest {

    private CorrelationProperties props;
    private AlertRepository alertRepository;
    private IntrusionInRestrictedZoneRule rule;

    @BeforeEach
    void setUp() {
        props = RuleTestFixtures.defaultProps();
        alertRepository = mock(AlertRepository.class);
        // No recent alert exists → cooldown never suppresses.
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new IntrusionInRestrictedZoneRule(props, alertRepository);
    }

    private List<AlertDraft> evaluatePersonInRestricted(double confidence) {
        Zone restricted = RuleTestFixtures.zone(1L, "Depot", ZoneType.RESTRICTED, 34.0, -6.85, 100);
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", confidence, 34.0, -6.85);
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(restricted)));
    }

    @ParameterizedTest(name = "confidence {0} → {1}")
    @CsvSource({
            "0.70, MEDIUM",
            "0.75, MEDIUM",
            "0.80, HIGH",
            "0.85, HIGH",
            "0.90, CRITICAL",
            "0.98, CRITICAL",
    })
    void gradesSeverityByConfidence(double confidence, AlertSeverity expected) {
        List<AlertDraft> drafts = evaluatePersonInRestricted(confidence);

        assertThat(drafts).hasSize(1);
        AlertDraft draft = drafts.get(0);
        assertThat(draft.type()).isEqualTo(AlertType.INTRUSION);
        assertThat(draft.severity()).isEqualTo(expected);
    }

    @Test
    void doesNotFireBelowConfidenceGate() {
        // Below the high-confidence threshold (0.70) the rule stays silent.
        assertThat(evaluatePersonInRestricted(0.65)).isEmpty();
        assertThat(evaluatePersonInRestricted(0.50)).isEmpty();
    }

    @Test
    void ignoresPersonOutsideRestrictedZone() {
        Zone station = RuleTestFixtures.zone(2L, "Platform", ZoneType.STATION, 34.0, -6.85, 100);
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.95, 34.0, -6.85);

        assertThat(rule.evaluate(CorrelationContext.forCamera(event, List.of(station)))).isEmpty();
    }

    @Test
    void respectsCooldown() {
        when(alertRepository.existsRecentByCameraLabelZone(
                eq(AlertType.INTRUSION), any(), any(), any(), any(Instant.class)))
                .thenReturn(true); // an intrusion alert was already raised recently

        assertThat(evaluatePersonInRestricted(0.95)).isEmpty();
    }
}
