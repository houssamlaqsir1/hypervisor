package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.ZoneDto;
import com.oncf.hypervisor.dto.ZoneRequest;
import com.oncf.hypervisor.service.ZoneAdminService;
import com.oncf.hypervisor.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only zone management. Restricted to {@code ROLE_ADMIN} by
 * {@code SecurityConfig} ({@code /api/admin/**}). Reads reuse the existing
 * {@link ZoneService} so admin and the public map see the same list.
 */
@RestController
@RequestMapping("/api/admin/zones")
@RequiredArgsConstructor
public class AdminZoneController {

    private final ZoneAdminService adminService;
    private final ZoneService zoneService;

    @GetMapping
    public List<ZoneDto> list() {
        return zoneService.findAll();
    }

    @PostMapping
    public ResponseEntity<ZoneDto> create(@Valid @RequestBody ZoneRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(req));
    }

    @PutMapping("/{id}")
    public ZoneDto update(@PathVariable Long id, @Valid @RequestBody ZoneRequest req) {
        return adminService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
