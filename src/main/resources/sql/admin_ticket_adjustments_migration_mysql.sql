-- Manual migration: review and apply before deploying the operation-ID API.
-- This script is not loaded automatically by the application.
-- Receipts are retained without automatic expiration. Do not attach user-delete
-- cascades: a receipt must survive deletion of either the operator or recipient.
-- No raw reason, token, purchase credential, or other request body is duplicated.
-- created_at / completed_at preserve the application's existing local-time
-- convention with microsecond precision; this migration does not change timezone policy.
-- Existing ticket history is unchanged; historical operation IDs are not fabricated.
CREATE TABLE admin_ticket_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    before_tickets INT NULL,
    after_tickets INT NULL,
    ledger_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    rejection_code VARCHAR(60) NULL,
    rejection_message VARCHAR(300) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_ticket_adjustment_operation UNIQUE (operation_id)
) ENGINE=InnoDB;

-- PROCESSING is only an uncommitted claim, never a successful API outcome.
-- Claim, ticket balance, ticket history, notification/outbox, audit, and COMPLETED
-- result commit together. Expected business rejections commit only a REJECTED receipt.
