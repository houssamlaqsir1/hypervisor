package com.oncf.hypervisor.domain.enums;

/**
 * Operator-facing lifecycle of an alert (report §3.3.3 — acquittement et
 * clôture d'une alerte): every alert is born {@link #NEW}, an operator
 * acknowledges it to signal "seen, being handled", then resolves it once
 * the incident is closed.
 */
public enum AlertStatus {
    NEW,
    ACKNOWLEDGED,
    RESOLVED
}
