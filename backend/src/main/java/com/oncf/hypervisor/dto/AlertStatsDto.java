package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.AlertSeverity;

import java.util.Map;

public record AlertStatsDto(
        long total,
        Map<AlertSeverity, Long> bySeverity
) {}
