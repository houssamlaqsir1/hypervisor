-- Adds a free-form JSON "details" column on alerts so the correlation engine
-- can persist the numeric breakdown behind each alert (fusion score, spatial
-- distance in metres, time delta, camera confidence, zone weight, etc.).
--
-- Stored as TEXT (not jsonb) to stay compatible with the existing Hibernate
-- mapping on Alert.message and to avoid pulling in a jsonb dialect just for
-- this. The application always writes/reads it through Jackson.

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS details TEXT;
