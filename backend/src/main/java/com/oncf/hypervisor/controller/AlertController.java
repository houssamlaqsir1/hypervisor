package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.AlertStatsDto;
import com.oncf.hypervisor.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @GetMapping
    public List<AlertDto> list(
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) Integer limit) {
        return service.search(severity, since, limit);
    }

    @GetMapping("/{id}")
    public AlertDto byId(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/stats")
    public AlertStatsDto stats() {
        return service.stats();
    }
}
