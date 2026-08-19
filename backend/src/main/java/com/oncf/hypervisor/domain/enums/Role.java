package com.oncf.hypervisor.domain.enums;

/**
 * The three human actors from the report mapped onto access levels:
 * <ul>
 *     <li>{@link #VIEWER} — read-only supervision (dashboard, map, history).
 *     Corresponds to a monitoring-only operator.</li>
 *     <li>{@link #OPERATOR} — everything a viewer can do, plus the alert
 *     lifecycle (acknowledge / resolve) and the operations console. The
 *     "opérateur de supervision".</li>
 *     <li>{@link #ADMIN} — everything, plus configuration (settings, camera
 *     and zone management). The "administrateur technique".</li>
 * </ul>
 *
 * <p>The "responsable sécurité / exploitation" actor is a VIEWER focused on
 * history and statistics — no separate technical privilege is needed.
 */
public enum Role {
    VIEWER,
    OPERATOR,
    ADMIN
}
