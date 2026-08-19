package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.AnalyticsDto;
import com.oncf.hypervisor.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Alert analytics for the "responsable sécurité". Read-only supervision —
 * any authenticated role may consult it.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;

    @GetMapping("/summary")
    public AnalyticsDto summary(@RequestParam(defaultValue = "30") int days) {
        return service.summary(days);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "30") int days) {
        byte[] body = service.exportCsv(days).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"alerts-last-" + days + "-days.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
