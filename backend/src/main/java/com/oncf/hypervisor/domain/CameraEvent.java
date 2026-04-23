package com.oncf.hypervisor.domain;

import com.oncf.hypervisor.domain.enums.CameraEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "camera_events", indexes = {
        @Index(name = "idx_cam_events_time", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CameraEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id", nullable = false, length = 64)
    private String cameraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private CameraEventType eventType;

    @Column(length = 128)
    private String label;

    @Column(nullable = false)
    private Double confidence;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
