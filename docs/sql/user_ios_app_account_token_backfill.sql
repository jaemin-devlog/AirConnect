-- users.ios_app_account_token backfill (MySQL 8.x)

START TRANSACTION;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ios_app_account_token VARCHAR(36) NULL;

UPDATE users
SET ios_app_account_token = UUID()
WHERE ios_app_account_token IS NULL OR ios_app_account_token = '';

ALTER TABLE users
    MODIFY COLUMN ios_app_account_token VARCHAR(36) NOT NULL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_ios_app_account_token (ios_app_account_token);

COMMIT;

