-- AirConnect IAP migration (MySQL 8.x)

-- 0) users: iOS App Account Token 추가 + 기존 사용자 backfill
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ios_app_account_token VARCHAR(36) NULL;

UPDATE users
SET ios_app_account_token = UUID()
WHERE ios_app_account_token IS NULL OR ios_app_account_token = '';

ALTER TABLE users
    MODIFY COLUMN ios_app_account_token VARCHAR(36) NOT NULL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_ios_app_account_token (ios_app_account_token);

CREATE TABLE IF NOT EXISTS iap_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    store VARCHAR(20) NOT NULL,
    product_id VARCHAR(120) NOT NULL,
    transaction_id VARCHAR(80) NULL,
    original_transaction_id VARCHAR(80) NULL,
    purchase_token VARCHAR(512) NULL,
    order_id VARCHAR(120) NULL,
    app_account_token VARCHAR(120) NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    granted_tickets INT NULL,
    before_tickets INT NULL,
    after_tickets INT NULL,
    verification_hash VARCHAR(100) NOT NULL,
    raw_payload_masked VARCHAR(1200) NOT NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_iap_orders_store_transaction UNIQUE (store, transaction_id),
    CONSTRAINT uk_iap_orders_store_purchase_token UNIQUE (store, purchase_token),
    INDEX idx_iap_orders_user_created (user_id, created_at),
    INDEX idx_iap_orders_store_product (store, product_id)
);

CREATE TABLE IF NOT EXISTS ticket_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_amount INT NOT NULL,
    before_amount INT NOT NULL,
    after_amount INT NOT NULL,
    reason VARCHAR(60) NOT NULL,
    ref_type VARCHAR(30) NOT NULL,
    ref_id VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_ticket_ledger_ref UNIQUE (ref_type, ref_id),
    INDEX idx_ticket_ledger_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS iap_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store VARCHAR(20) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    transaction_id VARCHAR(120) NULL,
    purchase_token VARCHAR(512) NULL,
    payload_hash VARCHAR(100) NOT NULL,
    payload_masked VARCHAR(1200) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_iap_events_store_created (store, created_at)
);

