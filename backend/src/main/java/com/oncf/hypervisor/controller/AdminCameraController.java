package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.CameraDto;
import com.oncf.hypervisor.dto.CameraRequest;
import com.oncf.hypervisor.service.CameraAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only camera management. Restricted to {@code ROLE_ADMIN} by
 * {@code SecurityConfig} ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/cameras")
@RequiredArgsConstructor
public class AdminCameraController {

    private final CameraAdminService service;

    @GetMapping
    public List<CameraDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<CameraDto> create(@Valid @RequestBody CameraRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public CameraDto update(@PathVariable Long id, @Valid @RequestBody CameraRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
