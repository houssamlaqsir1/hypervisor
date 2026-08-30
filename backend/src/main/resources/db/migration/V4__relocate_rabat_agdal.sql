-- Moves the "Rabat Agdal Platform" zone onto the position actually surveyed
-- on site at ONCF, where the live demo camera stands.
--
-- Why a migration rather than an edit to the seed loader: SeedDataLoader
-- inserts a zone only when no zone of that name exists, so that an
-- administrator's changes in the Zones screen survive a restart. That is the
-- right default, but it also means editing the seeded coordinates has no
-- effect on a database where the zone already exists — which is every
-- database this has ever run against. This relocates the existing row once.
--
-- CameraSeedLoader needs no equivalent: it upserts, so CAM-LIVE-1 follows
-- its seeded coordinates on the next boot.
--
-- Keeping the camera inside this zone matters. A detection landing outside
-- every zone produces no alert at all, because the zone type is what decides
-- whether a person is routine station activity or a track intrusion.

UPDATE zones
SET center_lat = 34.00461,
    center_lon = -6.85291
WHERE name = 'Rabat Agdal Platform';
