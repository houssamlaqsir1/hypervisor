package com.oncf.hypervisor.domain.enums;

public enum AlertType {
    INTRUSION,
    OBJECT_ON_TRACK,
    ESCALATION,
    ANOMALY,
    /** Cross-source confirmation: camera detection AND SIG event near each other. */
    FUSION,
    /** Behavior-model verdict: same object lingering in a small area far longer than a normal transit. */
    LOITERING,
    /** A person's bounding box flipped from upright to prone within a short window — possible collapse. */
    FALL_DETECTED,
    /** A person/animal detected close to (but not inside) a TRACK zone's boundary — approaching danger. */
    TRACK_PROXIMITY,
    MANUAL
}
