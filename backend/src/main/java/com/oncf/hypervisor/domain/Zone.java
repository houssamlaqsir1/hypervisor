package com.oncf.hypervisor.domain;

import com.oncf.hypervisor.domain.enums.ZoneType;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ZoneType type;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lon", nullable = false)
    private Double centerLon;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "height_m")
    private Double heightM;

    @Column(name = "is_tunnel")
    private Boolean isTunnel;

    @Column(name = "is_bridge")
    private Boolean isBridge;

    @Column(name = "geom_3d", columnDefinition = "geometry")
    private Geometry geom3d;

    public boolean contains(double lat, double lon) {
        double distance = haversine(centerLat, centerLon, lat, lon);
        return distance <= radiusM;
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
