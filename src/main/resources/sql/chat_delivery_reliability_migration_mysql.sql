-- Run before deploying the chat delivery reliability release.
CREATE TABLE IF NOT EXISTS chat_delivery_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(24) NOT NULL,
    room_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    user_id BIGINT NULL,
    lane VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_chat_delivery_due (next_attempt_at, id),
    INDEX idx_chat_delivery_lane (lane, id)
) ENGINE=InnoDB;

SET @chat_dispatch_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notification_outbox' AND column_name = 'dispatch_token'
);
SET @chat_dispatch_column_sql = IF(@chat_dispatch_column_exists > 0, 'SELECT 1',
    'ALTER TABLE notification_outbox ADD COLUMN dispatch_token VARCHAR(36) NULL');
PREPARE chat_dispatch_column_stmt FROM @chat_dispatch_column_sql;
EXECUTE chat_dispatch_column_stmt;
DEALLOCATE PREPARE chat_dispatch_column_stmt;
