package com.oncf.hypervisor.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate generates the alerts.type CHECK constraint from the AlertType enum
 * the very first time the schema is created and never refreshes it afterwards
 * (ddl-auto=update only adds tables/columns). When we later added the FUSION
 * value, the existing constraint stayed locked to the original list and any
 * INSERT with type='FUSION' fails with constraint [alerts_type_check].
 *
 * This runner drops every CHECK constraint on alerts.type and recreates it
 * with the full enum at every boot. Idempotent and cheap.
 */
@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class AlertTypeConstraintPatch implements ApplicationRunner {

    private static final String FIND_TYPE_CHECKS = """
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'public.alerts'::regclass
              AND contype = 'c'
              AND pg_get_constraintdef(oid) ILIKE '%type%'
            """;

    private static final String CREATE_CONSTRAINT = """
            ALTER TABLE alerts
                ADD CONSTRAINT alerts_type_check
                CHECK (type IN ('INTRUSION', 'OBJECT_ON_TRACK', 'ESCALATION', 'ANOMALY', 'FUSION', 'MANUAL'))
            """;

    private final JdbcTemplate jdbc;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            var existing = jdbc.queryForList(FIND_TYPE_CHECKS, String.class);
            for (String name : existing) {
                jdbc.execute("ALTER TABLE alerts DROP CONSTRAINT \"" + name + "\"");
                log.info("Dropped legacy alerts CHECK constraint '{}'", name);
            }
            jdbc.execute(CREATE_CONSTRAINT);
            log.info("alerts_type_check refreshed with FUSION + MANUAL");
        } catch (Exception ex) {
            log.warn("Could not refresh alerts_type_check (this is fatal for FUSION inserts): {}", ex.getMessage());
        }
    }
}
