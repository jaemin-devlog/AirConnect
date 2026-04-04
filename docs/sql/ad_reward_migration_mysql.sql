-- AirConnect Ad Reward migration (MySQL 8.x)

CREATE TABLE IF NOT EXISTS ad_reward_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    reward_amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(120) NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    rewarded_at DATETIME NULL,
    CONSTRAINT uk_ad_reward_sessions_session_key UNIQUE (session_key),
    CONSTRAINT uk_ad_reward_sessions_transaction_id UNIQUE (transaction_id),
    INDEX idx_ad_reward_sessions_user_created (user_id, created_at),
    INDEX idx_ad_reward_sessions_status_expires (status, expires_at)
);

CREATE TABLE IF NOT EXISTS ad_reward_callbacks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key VARCHAR(80) NULL,
    transaction_id VARCHAR(120) NULL,
    raw_query VARCHAR(2000) NOT NULL,
    signature_valid TINYINT(1) NOT NULL,
    received_at DATETIME NOT NULL,
    INDEX idx_ad_reward_callbacks_session_received (session_key, received_at),
    INDEX idx_ad_reward_callbacks_transaction (transaction_id)
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

-- 광고 보상은 ref_type='AD_REWARD_SESSION', ref_id='{sessionId}'를 사용한다.


