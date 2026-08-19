package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import com.oncf.hypervisor.domain.enums.AlertStatus;
import com.oncf.hypervisor.domain.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Severities already raised for the same (type, cameraId, zoneId, label)
     * since {@code since}. Lets a rule keep its cooldown for repeats while
     * still letting a <em>worse</em> alert through: a car that clipped the
     * rails (HIGH) and then rolled squarely onto them (CRITICAL) is new
     * information, not a duplicate.
     */
    @Query("""
            SELECT a.severity FROM Alert a
            WHERE a.type = :type
              AND a.createdAt >= :since
              AND a.cameraEvent IS NOT NULL
              AND a.cameraEvent.cameraId = :cameraId
              AND ((:zoneId IS NULL AND a.zone IS NULL) OR a.zone.id = :zoneId)
              AND ((:label IS NULL AND a.cameraEvent.label IS NULL)
                   OR a.cameraEvent.label = :label)
            """)
    List<AlertSeverity> recentSeveritiesByCameraLabelZone(@Param("type") AlertType type,
                                                          @Param("cameraId") String cameraId,
                                                          @Param("zoneId") Long zoneId,
                                                          @Param("label") String label,
                                                          @Param("since") Instant since);

    /**
     * Bulk-deletes every alert in the given status. Used for the "clear
     * resolved" housekeeping action, which is the only wholesale delete an
     * operator should reach for often: a closed incident has served its
     * purpose in the console, while an open one is still someone's problem.
     */
    long deleteByStatus(AlertStatus status);

    /**
     * Unlinks a zone from every alert that references it, returning how many
     * were changed.
     *
     * <p>Deleting a zone must not delete the incidents that happened in it —
     * that history is the record of what the system detected. Dropping the
     * foreign key instead keeps every alert, and the zone's name survives in
     * the alert message text ("Person in station 'X'"), so the timeline stays
     * readable after the zone itself is gone.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Alert a SET a.zone = null WHERE a.zone.id = :zoneId")
    int detachZone(@Param("zoneId") Long zoneId);

    @Query("SELECT a.severity, COUNT(a) FROM Alert a GROUP BY a.severity")
    List<Object[]> countBySeverity();

    /* ─── analytics aggregations (responsable sécurité) ─── */

    @Query("SELECT a.severity, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.severity")
    List<Object[]> countBySeveritySince(@Param("since") Instant since);

    @Query("SELECT a.type, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.type")
    List<Object[]> countByTypeSince(@Param("since") Instant since);

    @Query("SELECT a.status, COUNT(a) FROM Alert a WHERE a.createdAt >= :since GROUP BY a.status")
    List<Object[]> countByStatusSince(@Param("since") Instant since);

    /** Left join so alerts with no zone still count (grouped under a null name → "Unzoned" in Java). */
    @Query("SELECT z.name, COUNT(a) FROM Alert a LEFT JOIN a.zone z WHERE a.createdAt >= :since GROUP BY z.name")
    List<Object[]> countByZoneSince(@Param("since") Instant since);

    /** Daily alert counts over the window (Postgres date_trunc). Returns (yyyy-MM-dd, count) ordered by day. */
    @Query(value = """
            SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS day, COUNT(*) AS c
            FROM alerts
            WHERE created_at >= :since
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countByDaySince(@Param("since") Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
