package com.oncf.hypervisor.domain;

import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_created", columnList = "created_at"),
        @Index(name = "idx_alerts_severity", columnList = "severity")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    /**
     * Structured breakdown of <em>why</em> this alert fired, as JSON. For
     * FUSION alerts this carries the spatio-temporal score, the metric
     * distance / time delta, the camera confidence, the zone weight, and
     * the two event ids — everything the UI needs to render an honest
     * "correlation receipt" instead of a single opaque sentence.
     */
    @Column(columnDefinition = "text")
    private String details;

    private Double latitude;
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_event_id")
    private CameraEvent cameraEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sig_event_id")
    private SigEvent sigEvent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean dispatched;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;
}
