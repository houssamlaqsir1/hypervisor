package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findTop200ByOrderByCreatedAtDesc();
    List<Alert> findByOrderByCreatedAtDesc();
    List<Alert> findBySeverityOrderByCreatedAtDesc(AlertSeverity severity);
    List<Alert> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);
    List<Alert> findBySeverityAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            AlertSeverity severity,
            Instant since
    );

    boolean existsByTypeAndCameraEvent_Id(AlertType type, Long cameraEventId);

    boolean existsByTypeAndSigEvent_Id(AlertType type, Long sigEventId);

    /**
     * Cooldown probe for FUSION alerts: was an alert of the same type already
     * raised for the same (cameraId, sigSourceId, zoneId) triple within the
     * last {@code since}? Stops the same camera↔SIG pair from re-firing every
     * few seconds while it is still co-present.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Alert a
            WHERE a.type = :type
              AND a.createdAt >= :since
              AND a.cameraEvent IS NOT NULL
              AND a.cameraEvent.cameraId = :cameraId
              AND a.sigEvent IS NOT NULL
              AND a.sigEvent.sourceId = :sigSourceId
              AND ((a.zone IS NULL AND :zoneId IS NULL) OR a.zone.id = :zoneId)
            """)
    boolean existsRecentFusionForTriple(@Param("type") AlertType type,
                                        @Param("cameraId") String cameraId,
                                        @Param("sigSourceId") String sigSourceId,
                                        @Param("zoneId") Long zoneId,
                                        @Param("since") Instant since);

    /**
     * Cooldown probe used by the rule layer: was an alert of the same
     * {@code type} already raised for the given (cameraId, zoneId, label)
     * since {@code since}? Both {@code zoneId} and {@code label} are matched
     * with NULL-equivalence so the rules don't have to special-case missing
     * fields.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Alert a
            WHERE a.type = :type
              AND a.createdAt >= :since
              AND a.cameraEvent IS NOT NULL
              AND a.cameraEvent.cameraId = :cameraId
              AND ((:zoneId IS NULL AND a.zone IS NULL) OR a.zone.id = :zoneId)
              AND ((:label IS NULL AND a.cameraEvent.label IS NULL)
                   OR a.cameraEvent.label = :label)
            """)
    boolean existsRecentByCameraLabelZone(@Param("type") AlertType type,
                                          @Param("cameraId") String cameraId,
                                          @Param("zoneId") Long zoneId,
                                          @Param("label") String label,
                                          @Param("since") Instant since);

    @Query("SELECT a.severity, COUNT(a) FROM Alert a GROUP BY a.severity")
    List<Object[]> countBySeverity();
}
