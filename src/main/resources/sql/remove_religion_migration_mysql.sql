-- AirConnect religion profile field removal (MySQL 8.x)
-- Apply once after deploying the API change.

ALTER TABLE user_profiles
    DROP COLUMN religion;
