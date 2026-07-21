package com.oncf.hypervisor.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The {@code alerts.status} column was added after the table already had
 * rows; ddl-auto=update creates it as NULL for those. Backfill them to
 * NEW once per boot so the operator workflow treats pre-existing alerts
 * like any other open alert. Idempotent and cheap.
 */
@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class AlertStatusBackfillPatch implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            int patched = jdbc.update("UPDATE alerts SET status = 'NEW' WHERE status IS NULL");
            if (patched > 0) {
                log.info("Backfilled status=NEW on {} pre-existing alert(s)", patched);
            }
        } catch (Exception ex) {
            log.warn("Could not backfill alerts.status: {}", ex.getMessage());
        }
    }
}
