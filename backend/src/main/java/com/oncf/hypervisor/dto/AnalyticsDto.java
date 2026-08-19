package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertStatus;
import com.oncf.hypervisor.domain.enums.AlertType;

import java.util.List;
import java.util.Map;

/**
 * Aggregated alert analytics for the "responsable sécurité" actor —
 * statistics and trends by severity, type, zone, status and over time.
 */
public record AnalyticsDto(
        int windowDays,
        long total,
        Map<AlertSeverity, Long> bySeverity,
        Map<AlertType, Long> byType,
        Map<AlertStatus, Long> byStatus,
        List<Count> byZone,
        List<DailyCount> timeline
) {
    /** A generic labelled count (used for the by-zone breakdown). */
    public record Count(String label, long count) {}

    /** One day's alert total, {@code date} as yyyy-MM-dd. */
    public record DailyCount(String date, long count) {}
}
