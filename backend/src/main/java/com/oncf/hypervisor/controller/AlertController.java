package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.dto.AlertActionRequest;
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

    /** Operator acknowledges an alert — marks it as seen / being handled. */
    @PostMapping("/{id}/acknowledge")
    public AlertDto acknowledge(@PathVariable Long id,
                                @RequestBody(required = false) AlertActionRequest body) {
        String operator = body != null ? body.operator() : null;
        return service.acknowledge(id, operator);
    }

    /** Operator resolves / closes an alert, optionally with a note. */
    @PostMapping("/{id}/resolve")
    public AlertDto resolve(@PathVariable Long id,
                            @RequestBody(required = false) AlertActionRequest body) {
        String operator = body != null ? body.operator() : null;
        String note = body != null ? body.note() : null;
        return service.resolve(id, operator, note);
    }
}
