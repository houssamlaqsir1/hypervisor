package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.SigEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SigEventRepository extends JpaRepository<SigEvent, Long> {

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
