package com.oncf.hypervisor.service;

import com.oncf.hypervisor.domain.Camera;
import com.oncf.hypervisor.dto.CameraDto;
import com.oncf.hypervisor.dto.CameraRequest;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only CRUD for camera installations (report: "Administrateur
 * technique"). Replaces the edit-Java-and-restart workflow of
 * {@code CameraSeedLoader} — a camera created or moved here takes effect on
 * the very next event that camera reports, since {@code CameraEventService}
 * resolves each event's location from this registry.
 */
@Service
@RequiredArgsConstructor
public class CameraAdminService {

    private final CameraRepository cameraRepository;

    @Transactional(readOnly = true)
    public List<CameraDto> list() {
        return cameraRepository.findAll().stream()
                .sorted((a, b) -> a.getCameraId().compareToIgnoreCase(b.getCameraId()))
                .map(CameraAdminService::toDto)
                .toList();
    }

    @Transactional
    public CameraDto create(CameraRequest req) {
        String cameraId = req.cameraId().trim();
        if (cameraRepository.findByCameraId(cameraId).isPresent()) {
            throw new IllegalArgumentException("Camera id '" + cameraId + "' already exists");
        }
        Camera camera = Camera.builder()
                .cameraId(cameraId)
                .name(req.name().trim())
                .site(trimOrNull(req.site()))
                .latitude(req.latitude())
                .longitude(req.longitude())
                .elevationM(req.elevationM() != null ? req.elevationM() : 0.0)
                .headingDeg(req.headingDeg())
                .active(req.active() == null || req.active())
                .createdAt(Instant.now())
                .build();
        return toDto(cameraRepository.save(camera));
    }

    @Transactional
    public CameraDto update(Long id, CameraRequest req) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Camera " + id + " not found"));
        String cameraId = req.cameraId().trim();
        if (!cameraId.equalsIgnoreCase(camera.getCameraId())) {
            cameraRepository.findByCameraId(cameraId).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Camera id '" + cameraId + "' already exists");
                }
            });
        }
        camera.setCameraId(cameraId);
        camera.setName(req.name().trim());
        camera.setSite(trimOrNull(req.site()));
        camera.setLatitude(req.latitude());
        camera.setLongitude(req.longitude());
        camera.setElevationM(req.elevationM() != null ? req.elevationM() : 0.0);
        // Preserved when omitted: a surveyed bearing shouldn't be lost to a
        // client that simply doesn't know about the field.
        if (req.headingDeg() != null) camera.setHeadingDeg(req.headingDeg());
        if (req.active() != null) camera.setActive(req.active());
        return toDto(cameraRepository.save(camera));
    }

    @Transactional
    public void delete(Long id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Camera " + id + " not found"));
        cameraRepository.delete(camera);
    }

    private static String trimOrNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static CameraDto toDto(Camera c) {
        return new CameraDto(c.getId(), c.getCameraId(), c.getName(), c.getSite(),
                c.getLatitude(), c.getLongitude(), c.getElevationM(), c.getHeadingDeg(),
                c.isActive(), c.getCreatedAt());
    }
}
