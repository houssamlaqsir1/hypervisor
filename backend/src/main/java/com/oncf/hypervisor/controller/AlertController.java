package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.dto.AlertActionRequest;
import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.AlertStatsDto;
import com.oncf.hypervisor.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /**
     * Permanently removes one alert. Admin-only (see SecurityConfig) —
     * resolving an alert is how an operator closes an incident and keeps the
     * record; deleting is for entries that shouldn't be in the log at all.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /**
     * Clears the alert log. Admin-only.
     *
     * @param onlyResolved defaults to true, so the blunt "wipe everything"
     *                     has to be asked for explicitly rather than being
     *                     what a forgotten parameter gets you.
     */
    @DeleteMapping
    public DeletionResult deleteAll(
            @RequestParam(defaultValue = "true") boolean onlyResolved) {
        return new DeletionResult(service.deleteAll(onlyResolved));
    }

    /** How many alerts a bulk delete actually removed. */
    public record DeletionResult(long deleted) {}
}
