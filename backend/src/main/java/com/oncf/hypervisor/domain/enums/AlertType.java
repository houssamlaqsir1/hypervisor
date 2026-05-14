package com.oncf.hypervisor.domain.enums;

public enum AlertType {
    INTRUSION,
    OBJECT_ON_TRACK,
    ESCALATION,
    ANOMALY,
    /** Cross-source confirmation: camera detection AND SIG event near each other. */
    FUSION,
    MANUAL
}
