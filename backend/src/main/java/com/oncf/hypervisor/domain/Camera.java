package com.oncf.hypervisor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Fixed installation record for a physical (or virtual/demo) camera. In a
 * real deployment this is configured once when the camera is mounted —
 * exactly like surveying a CCTV camera's GPS position during installation
 * — so every event that camera reports afterwards is automatically
 * geolocated from here. No operator ever has to tell the system "this
 * feed is at the depot"; the system already knows because the camera was
 * registered there.
 */
@Entity
@Table(name = "cameras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External identifier events carry (e.g. {@code CAM-LIVE-1}) — matches {@code CameraEvent.cameraId}. */
    @Column(name = "camera_id", nullable = false, unique = true, length = 64)
    private String cameraId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 128)
    private String site;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
