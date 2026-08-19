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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Severity contract for {@link ObjectOnTrackRule} on a TRACK zone.
 *
 * <pre>
 * | class            | confidence | expected  |
 * |------------------|------------|-----------|
 * | person           | ≥ 0.85     | CRITICAL  |
 * | person           | 0.55–0.84  | HIGH      |
 * | vehicle          | ≥ 0.85     | CRITICAL  |
 * | large animal     | ≥ 0.85     | CRITICAL  |  (cow, horse — derailment risk)
 * | large animal     | 0.55–0.84  | HIGH      |
 * | medium animal    | any        | HIGH      |  (dog, sheep — impact damage)
 * | small animal     | any        | MEDIUM    |  (cat, bird — no train hazard)
 * | luggage          | ≥ 0.70     | HIGH      |
 * | luggage          | 0.55–0.69  | MEDIUM    |
 * </pre>
 *
 * <p>The animal tiers are the point: grading a cat CRITICAL alongside a cow
 * would teach operators that CRITICAL doesn't mean anything.
 *
 * <p>When the camera also reports {@code trackOverlap} — how much of the
 * object actually sits on the rails — that geometry takes over from
 * confidence for the classes that can block a train:
 *
 * <pre>
 * | trackOverlap | person / vehicle / large animal |
 * |--------------|---------------------------------|
 * | ≥ 0.50       | CRITICAL  (track blocked)       |
 * | 0.02–0.49    | HIGH      (glancing impact)     |
 * | &lt; 0.02       | no alert  (beside the rails)    |
 * </pre>
 */
class ObjectOnTrackRuleTest {

    private AlertRepository alertRepository;
    private ObjectOnTrackRule rule;

    @BeforeEach
    void setUp() {
        CorrelationProperties props = RuleTestFixtures.defaultProps();
        alertRepository = mock(AlertRepository.class);
        // No alert raised recently → nothing suppressed by the cooldown.
        when(alertRepository.recentSeveritiesByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(List.of());
        rule = new ObjectOnTrackRule(props, alertRepository);
    }

    private List<AlertDraft> onTrack(String label, double confidence) {
        return onTrack(label, confidence, null);
    }

    /** @param trackOverlap fraction of the object over the rails, or null for a camera that doesn't measure it. */
    private List<AlertDraft> onTrack(String label, double confidence, Double trackOverlap) {
        Zone track = RuleTestFixtures.zone(1L, "Tracks North", ZoneType.TRACK, 33.6, -7.58, 80);
        var event = RuleTestFixtures.event(CameraEventType.OBJECT_DETECTED, label, confidence, 33.6, -7.58);
        if (trackOverlap != null) {
            event.setRawPayload("{\"source\":\"yolov8_detector\",\"trackOverlap\":" + trackOverlap + "}");
        }
        return rule.evaluate(CorrelationContext.forCamera(event, List.of(track)));
    }

    @ParameterizedTest(name = "{0} @ {1} → {2}")
    @CsvSource({
            "person,  0.90, CRITICAL",
            "person,  0.60, HIGH",
            "car,     0.90, CRITICAL",
            "car,     0.60, HIGH",
            "backpack,0.80, HIGH",
            "backpack,0.60, MEDIUM",
    })
    void gradesSeverityByClassAndConfidence(String label, double confidence, AlertSeverity expected) {
        List<AlertDraft> drafts = onTrack(label, confidence);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(AlertType.OBJECT_ON_TRACK);
        assertThat(drafts.get(0).severity()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} @ {1} → {2}")
    @CsvSource({
            // Large animals are a genuine derailment risk — treated like a person.
            "cow,   0.90, CRITICAL",
            "horse, 0.90, CRITICAL",
            "cow,   0.60, HIGH",
            // Medium animals can damage the train front — worth stopping for.
            "dog,   0.90, HIGH",
            "dog,   0.60, HIGH",
            "sheep, 0.90, HIGH",
            // Small animals are a welfare concern, not a train hazard.
            "cat,   0.90, MEDIUM",
            "cat,   0.60, MEDIUM",
            "bird,  0.95, MEDIUM",
    })
    void gradesAnimalsBySizeNotJustByBeingAnimals(
            String label, double confidence, AlertSeverity expected) {
        List<AlertDraft> drafts = onTrack(label, confidence);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).severity()).isEqualTo(expected);
    }

    @Test
    void catIsLessSevereThanDogWhichIsLessSevereThanCow() {
        // The whole point of the size tiers, asserted as an ordering.
        AlertSeverity cat = onTrack("cat", 0.9).get(0).severity();
        AlertSeverity dog = onTrack("dog", 0.9).get(0).severity();
        AlertSeverity cow = onTrack("cow", 0.9).get(0).severity();

        assertThat(cat.rank()).isLessThan(dog.rank());
        assertThat(dog.rank()).isLessThan(cow.rank());
    }

    @Test
    void unknownAnimalDefaultsToTheCautiousMiddle() {
        // Assuming an unrecognised animal is harmless is the dangerous mistake.
        assertThat(onTrack("horse", 0.6)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.HIGH);
    }

    @Test
    void ignoresIrrelevantClasses() {
        // "chair" is COCO noise → not a track hazard.
        assertThat(onTrack("chair", 0.99)).isEmpty();
    }

    @Test
    void ignoresBelowMinimumConfidence() {
        assertThat(onTrack("person", 0.40)).isEmpty();
    }

    /* ── geometry-graded severity (cameras with a track polygon) ────── */

    @ParameterizedTest(name = "car {1} on the rails → {2}")
    @CsvSource({
            // A car across the rails blocks the train, whatever the confidence.
            "car, 0.95, CRITICAL, 0.90",
            "car, 0.60, CRITICAL, 0.55",
            "car, 0.95, CRITICAL, 0.50",
            // Nosing onto the crossing, or almost clear of it: the train would
            // clip it, not hit a wall.
            "car, 0.95, HIGH,     0.49",
            "car, 0.95, HIGH,     0.15",
            "car, 0.60, HIGH,     0.30",
    })
    void gradesVehiclesByHowMuchOfThemIsOnTheRails(
            String label, double confidence, AlertSeverity expected, double overlap) {
        assertThat(onTrack(label, confidence, overlap)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(expected);
    }

    @Test
    void geometryOutranksConfidenceWhenItIsAvailable() {
        // Same 95%-confident car; only the geometry differs. Without the
        // measurement both would have been CRITICAL on confidence alone.
        AlertSeverity clipping = onTrack("car", 0.95, 0.10).get(0).severity();
        AlertSeverity across = onTrack("car", 0.95, 0.80).get(0).severity();

        assertThat(clipping).isEqualTo(AlertSeverity.HIGH);
        assertThat(across).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void doesNotFireForAnObjectStandingBesideTheRails() {
        // Inside the track zone (the zone is a circle on a map) but measurably
        // clear of the rails themselves — that is not an object *on* the track.
        assertThat(onTrack("car", 0.99, 0.0)).isEmpty();
        assertThat(onTrack("person", 0.99, 0.01)).isEmpty();
    }

    @Test
    void partialOverlapStillMentionsTheCoverageInTheMessage() {
        // The operator has to be able to see *why* it was graded HIGH.
        assertThat(onTrack("car", 0.90, 0.25).get(0).message())
                .contains("25% on the rails")
                .contains("partly fouling the track");
    }

    @Test
    void smallAnimalStaysMediumEvenLyingAcrossTheRails() {
        // Geometry grades the hazard, it doesn't invent one: a cat squarely on
        // the rails still cannot derail a train.
        assertThat(onTrack("cat", 0.95, 0.95)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.MEDIUM);
    }

    /* ── cooldown ───────────────────────────────────────────────────── */

    @Test
    void suppressesARepeatAtTheSameSeverity() {
        when(alertRepository.recentSeveritiesByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(List.of(AlertSeverity.HIGH));

        assertThat(onTrack("car", 0.95, 0.20)).isEmpty();
    }

    @Test
    void letsAnEscalationThroughTheCooldown() {
        // The car clipped the rails a minute ago (HIGH) and has now rolled
        // across them. Swallowing that as a duplicate would hide the emergency.
        when(alertRepository.recentSeveritiesByCameraLabelZone(
                any(AlertType.class), any(), any(), any(), any(Instant.class)))
                .thenReturn(List.of(AlertSeverity.HIGH));

        assertThat(onTrack("car", 0.95, 0.80)).singleElement()
                .extracting(AlertDraft::severity)
                .isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void doesNotFireOffTrack() {
        Zone station = RuleTestFixtures.zone(2L, "Platform", ZoneType.STATION, 33.6, -7.58, 120);
        var event = RuleTestFixtures.event(CameraEventType.OBJECT_DETECTED, "person", 0.95, 33.6, -7.58);

        assertThat(rule.evaluate(CorrelationContext.forCamera(event, List.of(station)))).isEmpty();
    }
}
