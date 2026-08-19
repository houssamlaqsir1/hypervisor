package com.oncf.hypervisor.service.correlation.rules;

import com.oncf.hypervisor.config.CorrelationProperties;
import com.oncf.hypervisor.domain.CameraEvent;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NightActivityRule} — the same detection means something different
 * at 03:00 than at 17:00.
 *
 * <pre>
 * | when   | zone            | class   | expected   |
 * |--------|-----------------|---------|------------|
 * | 03:00  | STATION         | person  | MEDIUM     |
 * | 03:00  | STATION         | vehicle | HIGH       |
 * | 14:00  | STATION         | person  | (no alert) |
 * | 03:00  | TRACK/RESTRICTED| person  | (no alert — already covered) |
 * </pre>
 */
class NightActivityRuleTest {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private AlertRepository alertRepository;
    private NightActivityRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps(); // 23:00–05:00
        alertRepository = mock(AlertRepository.class);
        when(alertRepository.existsRecentByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(false);
        rule = new NightActivityRule(props, alertRepository);
    }

    /** An instant at the given local hour in ONCF's timezone. */
    private static Instant atLocalHour(int hour) {
        return LocalDateTime.now(CASABLANCA)
                .withHour(hour).withMinute(30).withSecond(0).withNano(0)
                .atZone(CASABLANCA)
                .toInstant();
    }

    private List<AlertDraft> evaluate(int localHour, ZoneType zoneType, String label) {
        Zone zone = RuleTestFixtures.zone(1L, "Platform", zoneType, 33.6, -7.58, 120);
        CameraEvent e = RuleTestFixtures.event(
                CameraEventType.HUMAN_DETECTED, label, 0.9, 33.6, -7.58);
        e.setOccurredAt(atLocalHour(localHour));
        return rule.evaluate(CorrelationContext.forCamera(e, List.of(zone)));
    }

    @Test
    void personInStationAtNightIsMedium() {
        List<AlertDraft> drafts = evaluate(3, ZoneType.STATION, "person");
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.NIGHT_ACTIVITY);
        assertThat(drafts.get(0).severity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    @Test
    void vehicleAtNightIsHigherThanAPerson() {
        // A car on a closed platform is harder to explain innocently.
        assertThat(evaluate(3, ZoneType.STATION, "car")).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void staysSilentDuringOpeningHours() {
        assertThat(evaluate(14, ZoneType.STATION, "person")).isEmpty();
        assertThat(evaluate(9, ZoneType.STATION, "person")).isEmpty();
    }

    @Test
    void firesRightAfterClosingAndBeforeReopening() {
        // Window wraps past midnight — the naive range check gets this wrong.
        assertThat(evaluate(23, ZoneType.STATION, "person")).hasSize(1);
        assertThat(evaluate(4, ZoneType.STATION, "person")).hasSize(1);
        assertThat(evaluate(5, ZoneType.STATION, "person")).isEmpty(); // reopened
    }

    @Test
    void doesNotDoubleUpOnZonesAlreadyCoveredRoundTheClock() {
        // TRACK/RESTRICTED are already HIGH/CRITICAL day and night via the
        // intrusion / object-on-track rules — re-flagging is just noise.
        assertThat(evaluate(3, ZoneType.TRACK, "person")).isEmpty();
        assertThat(evaluate(3, ZoneType.RESTRICTED, "person")).isEmpty();
    }

    @Test
    void ignoresIrrelevantClasses() {
        assertThat(evaluate(3, ZoneType.STATION, "backpack")).isEmpty();
    }
}
