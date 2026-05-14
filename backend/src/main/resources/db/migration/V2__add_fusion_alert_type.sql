-- Refresh the alerts.type CHECK constraint so Hibernate can insert the new
-- FUSION alert type produced by CameraSigFusionRule. Hibernate ddl-auto=update
-- never recreates check constraints, so we do it explicitly here.

DO $$
DECLARE
    cname text;
BEGIN
    FOR cname IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.alerts'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%type%'
    LOOP
        EXECUTE format('ALTER TABLE alerts DROP CONSTRAINT %I', cname);
    END LOOP;
END
$$;

ALTER TABLE alerts
    ADD CONSTRAINT alerts_type_check
    CHECK (type IN ('INTRUSION', 'OBJECT_ON_TRACK', 'ESCALATION', 'ANOMALY', 'FUSION', 'MANUAL'));
