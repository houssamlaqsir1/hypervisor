package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.CameraEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CameraEventRepository extends JpaRepository<CameraEvent, Long> {

    @Query("""
            SELECT COUNT(c) FROM CameraEvent c
            WHERE c.occurredAt >= :since
              AND SQRT(POWER(c.latitude - :lat, 2) + POWER(c.longitude - :lon, 2)) < :radiusDeg
            """)
    long countNearby(@Param("lat") double lat,
                     @Param("lon") double lon,
                     @Param("radiusDeg") double radiusDeg,
                     @Param("since") Instant since);

    /** Recent camera events near a point (latest first). Used by fusion rules. */
    @Query("""
            SELECT c FROM CameraEvent c
            WHERE c.occurredAt >= :since
              AND SQRT(POWER(c.latitude - :lat, 2) + POWER(c.longitude - :lon, 2)) < :radiusDeg
            ORDER BY c.occurredAt DESC
            """)
    List<CameraEvent> findNearbyRecent(@Param("lat") double lat,
                                       @Param("lon") double lon,
                                       @Param("radiusDeg") double radiusDeg,
                                       @Param("since") Instant since,
                                       Pageable pageable);
}
