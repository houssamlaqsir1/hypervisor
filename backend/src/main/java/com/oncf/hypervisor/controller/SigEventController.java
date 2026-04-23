package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.SigEventRequest;
import com.oncf.hypervisor.service.SigEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sig-events")
@RequiredArgsConstructor
public class SigEventController {

    private final SigEventService service;

    @PostMapping
    public ResponseEntity<SigEventService.IngestionResult> ingest(@Valid @RequestBody SigEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingest(req));
    }
}
