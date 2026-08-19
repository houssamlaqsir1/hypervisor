package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.SigEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SigEventRepository extends JpaRepository<SigEvent, Long> {

    /**
     * Unlinks a zone from every SIG event that references it, returning how
     * many were changed. Same reasoning as {@link AlertRepository#detachZone}:
     * removing a zone from the configuration must not erase the events
     * recorded while it existed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SigEvent s SET s.zone = null WHERE s.zone.id = :zoneId")
    int detachZone(@Param("zoneId") Long zoneId);

    /** Recent SIG events near a point (latest first). Used by fusion rules. */
    @Query("""
            SELECT s FROM SigEvent s
            WHERE s.occurredAt >= :since
              AND SQRT(POWER(s.latitude - :lat, 2) + POWER(s.longitude - :lon, 2)) < :radiusDeg
            ORDER BY s.occurredAt DESC
            """)
    List<SigEvent> findNearbyRecent(@Param("lat") double lat,
                                    @Param("lon") double lon,
                                    @Param("radiusDeg") double radiusDeg,
                                    @Param("since") Instant since,
                                    Pageable pageable);
}
