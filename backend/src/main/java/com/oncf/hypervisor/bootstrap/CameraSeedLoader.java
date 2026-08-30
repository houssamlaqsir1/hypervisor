package com.oncf.hypervisor.bootstrap;

import com.oncf.hypervisor.domain.Camera;
import com.oncf.hypervisor.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Registers each camera's fixed installation location — exactly like
 * surveying a physical CCTV camera's GPS position when it's mounted. Once
 * a camera is registered here, every event it reports is automatically
 * geolocated from this record (see {@link com.oncf.hypervisor.service.CameraEventService}),
 * so nothing about "where did this happen" is ever picked manually at
 * request time.
 *
 * <p>This runs on every boot and <b>upserts</b> — if you move
 * {@code CAM-LIVE-1} (the phone) to a new test spot, just edit its
 * latitude/longitude below and restart the backend; the existing row gets
 * updated in place rather than only inserting when missing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class CameraSeedLoader implements CommandLineRunner {

    private final CameraRepository cameraRepository;

    @Override
    public void run(String... args) {
        Instant now = Instant.now();
        List<Camera> seeds = List.of(
                // Positioned at the centre of the "Rabat Agdal Platform" zone
                // (see SeedDataLoader). A camera outside every zone still
                // detects, but no zone rule can fire for it — so if you move
                // this one, move it to somewhere a zone actually covers.
                Camera.builder()
                        .cameraId("CAM-LIVE-1")
                        .name("Live demo feed")
                        .site("Rabat Agdal")
                        .latitude(34.00461).longitude(-6.85291).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-CASA-VOY-01")
                        .name("Casablanca Voyageurs — Platform")
                        .site("Casablanca Voyageurs")
                        .latitude(33.5951).longitude(-7.6188).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-CASA-VOY-02")
                        .name("Casablanca Voyageurs — Tracks North")
                        .site("Casablanca Voyageurs")
                        .latitude(33.5983).longitude(-7.5805).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-DEPOT-01")
                        .name("Technical Depot — Entrance")
                        .site("Casablanca")
                        .latitude(33.5905).longitude(-7.6023).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-SIGNAL-A")
                        .name("Signal Box A")
                        .site("Casablanca")
                        .latitude(33.6010).longitude(-7.5750).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-FES-01")
                        .name("Fes Station — Platform")
                        .site("Fes")
                        .latitude(34.0331).longitude(-5.0003).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-MARRAKECH-01")
                        .name("Marrakech Station — Platform")
                        .site("Marrakech")
                        .latitude(31.6295).longitude(-7.9811).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-TANGER-01")
                        .name("Tanger Ville — Platform")
                        .site("Tanger")
                        .latitude(35.7595).longitude(-5.8340).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-KENITRA-01")
                        .name("Kenitra Station — Platform")
                        .site("Kenitra")
                        .latitude(34.2610).longitude(-6.5802).elevationM(0.0)
                        .active(true).createdAt(now).build(),
                Camera.builder()
                        .cameraId("CAM-OUJDA-01")
                        .name("Oujda Station — Platform")
                        .site("Oujda")
                        .latitude(34.6805).longitude(-1.9450).elevationM(0.0)
                        .active(true).createdAt(now).build()
        );

        int created = 0;
        int updated = 0;
        for (Camera seed : seeds) {
            var existing = cameraRepository.findByCameraId(seed.getCameraId());
            if (existing.isEmpty()) {
                cameraRepository.save(seed);
                created++;
                continue;
            }
            Camera c = existing.get();
            boolean changed = !c.getLatitude().equals(seed.getLatitude())
                    || !c.getLongitude().equals(seed.getLongitude())
                    || !java.util.Objects.equals(c.getElevationM(), seed.getElevationM())
                    || !c.getName().equals(seed.getName())
                    || !java.util.Objects.equals(c.getSite(), seed.getSite());
            if (changed) {
                c.setName(seed.getName());
                c.setSite(seed.getSite());
                c.setLatitude(seed.getLatitude());
                c.setLongitude(seed.getLongitude());
                c.setElevationM(seed.getElevationM());
                cameraRepository.save(c);
                updated++;
            }
        }
        log.info("Seed data: {} new camera(s) registered, {} updated, {} unchanged",
                created, updated, seeds.size() - created - updated);
    }
}
