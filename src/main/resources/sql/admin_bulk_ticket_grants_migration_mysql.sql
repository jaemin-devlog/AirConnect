-- Apply once before deploying the admin all-member ticket grant API.
-- One receipt represents one logical operation. Per-user balance changes remain in ticket_ledger.
CREATE TABLE admin_bulk_ticket_grants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    target_count INT NULL,
    granted_count INT NULL,
    total_granted_tickets BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_bulk_ticket_grant_operation UNIQUE (operation_id)
) ENGINE=InnoDB;

-- The claim, all eligible-user balance changes, per-user ticket history,
-- notifications/outbox, audit log and COMPLETED receipt commit in one transaction.
