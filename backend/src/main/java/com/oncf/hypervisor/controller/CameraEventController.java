package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.CameraEventRequest;
import com.oncf.hypervisor.service.CameraEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/camera-events")
@RequiredArgsConstructor
public class CameraEventController {

    private final CameraEventService service;

    @PostMapping
    public ResponseEntity<CameraEventService.IngestionResult> ingest(@Valid @RequestBody CameraEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingest(req));
    }
}
