package com.oncf.hypervisor.domain.enums;

public enum AlertSeverity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    AlertSeverity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public AlertSeverity escalated() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH, CRITICAL -> CRITICAL;
        };
    }
}
