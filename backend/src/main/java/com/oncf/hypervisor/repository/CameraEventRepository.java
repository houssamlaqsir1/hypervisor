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

    /**
     * Same as {@link #countNearby} but ignores low-confidence noise (flicker,
     * background objects) so the escalation rule reacts to genuine repeated
     * detections rather than raw frame volume.
     */
    @Query("""
            SELECT COUNT(c) FROM CameraEvent c
            WHERE c.occurredAt >= :since
              AND c.confidence >= :minConfidence
              AND SQRT(POWER(c.latitude - :lat, 2) + POWER(c.longitude - :lon, 2)) < :radiusDeg
            """)
    long countSignificantNearby(@Param("lat") double lat,
                                @Param("lon") double lon,
                                @Param("radiusDeg") double radiusDeg,
                                @Param("since") Instant since,
                                @Param("minConfidence") double minConfidence);

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

    /**
     * Recent events for the same camera + object label, newest first. Used
     * by {@code LoiteringBehaviorRule} to reconstruct a track's position
     * history and feed it to the behavior model. Callers should reverse the
     * result to chronological order before extracting features.
     */
    @Query("""
            SELECT c FROM CameraEvent c
            WHERE c.cameraId = :cameraId
              AND c.label = :label
              AND c.occurredAt >= :since
            ORDER BY c.occurredAt DESC
            """)
    List<CameraEvent> findTrackHistory(@Param("cameraId") String cameraId,
                                       @Param("label") String label,
                                       @Param("since") Instant since,
                                       Pageable pageable);
}
