package com.oncf.hypervisor.service.correlation;

import com.oncf.hypervisor.domain.CameraEvent;

import java.util.Locale;
import java.util.Map;

/**
 * Maps the raw class labels coming off the AI cameras (COCO-SSD, YOLO,
 * Xeoma, …) onto the high-level categories the correlation rules reason
 * about. Centralising the mapping here means {@link
 * com.oncf.hypervisor.service.correlation.rules.IntrusionInRestrictedZoneRule},
 * {@link com.oncf.hypervisor.service.correlation.rules.ObjectOnTrackRule},
 * {@link com.oncf.hypervisor.service.correlation.rules.ModerateStationActivityRule}
 * and the others can't drift on what counts as "a person" vs "an animal".
 *
 * <p>Anything we don't recognise falls into {@link Category#OTHER}; rules
 * that care about typed categories simply ignore those events, and rules
 * that operate purely on {@code CameraEventType} (legacy / generic
 * intrusion) still fire normally.
 */
public final class CameraClassTaxonomy {

    private CameraClassTaxonomy() {
        /* utility */
    }

    public enum Category {
        PERSON,
        ANIMAL,
        VEHICLE,
        LUGGAGE,
        OTHER
    }

    private static final Map<String, Category> LABEL_TO_CATEGORY = Map.ofEntries(
            Map.entry("person", Category.PERSON),
            Map.entry("human", Category.PERSON),
            Map.entry("face", Category.PERSON),
            Map.entry("people", Category.PERSON),
            Map.entry("pedestrian", Category.PERSON),

            Map.entry("dog", Category.ANIMAL),
            Map.entry("cat", Category.ANIMAL),
            Map.entry("bird", Category.ANIMAL),
            Map.entry("horse", Category.ANIMAL),
            Map.entry("cow", Category.ANIMAL),
            Map.entry("sheep", Category.ANIMAL),

            Map.entry("bicycle", Category.VEHICLE),
            Map.entry("bike", Category.VEHICLE),
            Map.entry("car", Category.VEHICLE),
            Map.entry("motorcycle", Category.VEHICLE),
            Map.entry("motorbike", Category.VEHICLE),
            Map.entry("bus", Category.VEHICLE),
            Map.entry("truck", Category.VEHICLE),
            Map.entry("train", Category.VEHICLE),
            Map.entry("boat", Category.VEHICLE),

            Map.entry("backpack", Category.LUGGAGE),
            Map.entry("handbag", Category.LUGGAGE),
            Map.entry("suitcase", Category.LUGGAGE)
    );

    private static final Map<Category, String> CATEGORY_FALLBACK = Map.of(
            Category.PERSON, "Person",
            Category.ANIMAL, "Animal",
            Category.VEHICLE, "Vehicle",
            Category.LUGGAGE, "Unattended luggage",
            Category.OTHER, "Object"
    );

    /**
     * Returns the {@link Category} for a raw label string (case-insensitive),
     * or {@link Category#OTHER} when nothing matches.
     */
    public static Category classify(String label) {
        if (label == null) return Category.OTHER;
        String key = label.toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) return Category.OTHER;
        return LABEL_TO_CATEGORY.getOrDefault(key, Category.OTHER);
    }

    /**
     * Returns a human-friendly label suitable for an alert message. Uses the
     * raw label when present (capitalised) so the operator sees the same
     * wording the AI model produced; otherwise falls back to the category's
     * generic name so alerts without a label still read naturally.
     */
    public static String display(CameraEvent e) {
        if (e == null) return "Unknown";
        String label = e.getLabel();
        if (label != null && !label.isBlank()) {
            String trimmed = label.trim();
            return Character.toUpperCase(trimmed.charAt(0))
                    + trimmed.substring(1).toLowerCase(Locale.ROOT);
        }
        return CATEGORY_FALLBACK.getOrDefault(classify(null), "Object");
    }
}
