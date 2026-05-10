-- AirConnect social login device binding migration (MySQL 8.x)
-- 목적:
-- 1) 하나의 deviceId를 하나의 사용자에게만 귀속
-- 2) 동일 디바이스에서 서로 다른 소셜 계정 생성/로그인 차단

CREATE TABLE IF NOT EXISTS social_login_device_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_login_device_bindings_device UNIQUE (device_id),
    INDEX idx_social_login_device_bindings_user (user_id)
);
