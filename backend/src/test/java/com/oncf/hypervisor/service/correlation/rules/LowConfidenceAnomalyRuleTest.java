package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.domain.enums.CameraEventType;
import com.oncf.hypervisor.service.correlation.AlertDraft;
import com.oncf.hypervisor.service.correlation.CorrelationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LowConfidenceAnomalyRule} — an explicit ANOMALY report is logged as
 * LOW (noted, not urgent); anything else is ignored here.
 */
class LowConfidenceAnomalyRuleTest {

    private final LowConfidenceAnomalyRule rule = new LowConfidenceAnomalyRule();

    @Test
    void anomalyIsAlwaysLow() {
        var event = RuleTestFixtures.event(CameraEventType.ANOMALY, "unknown", 0.4, 34.0, -6.85);
        List<AlertDraft> drafts = rule.evaluate(CorrelationContext.forCamera(event, List.of()));

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.ANOMALY);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.LOW);
    }

    @Test
    void ignoresNonAnomalyEvents() {
        var event = RuleTestFixtures.event(CameraEventType.HUMAN_DETECTED, "person", 0.9, 34.0, -6.85);
        assertThat(rule.evaluate(CorrelationContext.forCamera(event, List.of()))).isEmpty();
    }
}
