package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertStatus;
import com.oncf.hypervisor.domain.enums.AlertType;
import com.oncf.hypervisor.dto.AnalyticsDto;
import com.oncf.hypervisor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only alert analytics for the "responsable sécurité" actor. Any
 * authenticated user can consult it — supervision, not configuration.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_WINDOW_DAYS = 365;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public AnalyticsDto summary(int windowDays) {
        int days = Math.min(Math.max(windowDays, 1), MAX_WINDOW_DAYS);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        Map<AlertSeverity, Long> bySeverity = new EnumMap<>(AlertSeverity.class);
        for (AlertSeverity s : AlertSeverity.values()) bySeverity.put(s, 0L);
        for (Object[] row : alertRepository.countBySeveritySince(since)) {
            bySeverity.put((AlertSeverity) row[0], (Long) row[1]);
        }

        Map<AlertType, Long> byType = new EnumMap<>(AlertType.class);
        for (Object[] row : alertRepository.countByTypeSince(since)) {
            byType.put((AlertType) row[0], (Long) row[1]);
        }

        Map<AlertStatus, Long> byStatus = new EnumMap<>(AlertStatus.class);
        for (AlertStatus st : AlertStatus.values()) byStatus.put(st, 0L);
        for (Object[] row : alertRepository.countByStatusSince(since)) {
            if (row[0] != null) byStatus.put((AlertStatus) row[0], (Long) row[1]);
        }

        List<AnalyticsDto.Count> byZone = new ArrayList<>();
        for (Object[] row : alertRepository.countByZoneSince(since)) {
            String name = row[0] != null ? (String) row[0] : "Unzoned";
            byZone.add(new AnalyticsDto.Count(name, (Long) row[1]));
        }
        byZone.sort(Comparator.comparingLong(AnalyticsDto.Count::count).reversed());

        List<AnalyticsDto.DailyCount> timeline = buildTimeline(days, since);

        long total = alertRepository.countByCreatedAtGreaterThanEqual(since);

        return new AnalyticsDto(days, total, bySeverity, byType, byStatus, byZone, timeline);
    }

    /**
     * A dense daily series: every day in the window is present (0 when no
     * alerts) so the front-end chart has no gaps.
     */
    private List<AnalyticsDto.DailyCount> buildTimeline(int days, Instant since) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : alertRepository.countByDaySince(since)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        List<AnalyticsDto.DailyCount> series = new ArrayList<>();
        LocalDate start = LocalDate.ofInstant(since, ZoneOffset.UTC);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            String key = d.format(DAY);
            series.add(new AnalyticsDto.DailyCount(key, counts.getOrDefault(key, 0L)));
        }
        return series;
    }

    /**
     * Raw alert rows as CSV for the responsable sécurité to do their own
     * offline analysis (Excel, etc.). Newest first, over the same window.
     */
    @Transactional(readOnly = true)
    public String exportCsv(int windowDays) {
        int days = Math.min(Math.max(windowDays, 1), MAX_WINDOW_DAYS);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Alert> alerts = alertRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since);

        StringBuilder sb = new StringBuilder();
        sb.append("id,createdAt,severity,type,status,zone,acknowledgedBy,resolvedBy,message\n");
        for (Alert a : alerts) {
            sb.append(a.getId()).append(',')
                    .append(a.getCreatedAt()).append(',')
                    .append(a.getSeverity()).append(',')
                    .append(a.getType()).append(',')
                    .append(a.getStatus() != null ? a.getStatus() : "").append(',')
                    .append(csv(a.getZone() != null ? a.getZone().getName() : "")).append(',')
                    .append(csv(a.getAcknowledgedBy())).append(',')
                    .append(csv(a.getResolvedBy())).append(',')
                    .append(csv(a.getMessage()))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Quote a CSV field if it contains a comma, quote, or newline. */
    private static String csv(String v) {
        if (v == null || v.isEmpty()) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
