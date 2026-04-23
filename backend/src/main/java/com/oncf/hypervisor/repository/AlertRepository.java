package com.oncf.hypervisor.repository;

import com.oncf.hypervisor.domain.Alert;
import com.oncf.hypervisor.domain.enums.AlertSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
            SELECT a FROM Alert a
            WHERE (:severity IS NULL OR a.severity = :severity)
              AND (:since IS NULL OR a.createdAt >= :since)
            ORDER BY a.createdAt DESC
            """)
    List<Alert> search(@Param("severity") AlertSeverity severity,
                       @Param("since") Instant since);

    @Query("SELECT a.severity, COUNT(a) FROM Alert a GROUP BY a.severity")
    List<Object[]> countBySeverity();
}
