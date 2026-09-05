-- Manual migration for an EXISTING maintenance_settings table, before deploying
-- the versioned maintenance API. Not applied automatically. Run only once after
-- checking information_schema.columns for maintenance_settings.version.
ALTER TABLE maintenance_settings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- No singleton seed is needed: a null Java @Version marks the initial insert,
-- exposed as virtual version -1 until saved. Existing rows start at version 0.
