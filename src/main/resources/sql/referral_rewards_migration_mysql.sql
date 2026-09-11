-- 마이페이지 추천인 코드와 양쪽 5장 보상 이력을 저장한다.
-- 애플리케이션 배포 전에 대상 DB에서 1회 실행한다.

CREATE TABLE IF NOT EXISTS referral_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code VARCHAR(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_referral_code_user UNIQUE (user_id),
    CONSTRAINT uk_referral_code_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS referral_redemptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    referrer_user_id BIGINT NOT NULL,
    referred_user_id BIGINT NOT NULL,
    referral_code VARCHAR(6) NOT NULL,
    pair_low_user_id BIGINT NOT NULL,
    pair_high_user_id BIGINT NOT NULL,
    reward_tickets INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_referral_referred_user UNIQUE (referred_user_id),
    CONSTRAINT uk_referral_user_pair UNIQUE (pair_low_user_id, pair_high_user_id),
    CONSTRAINT chk_referral_distinct_users CHECK (referrer_user_id <> referred_user_id),
    CONSTRAINT chk_referral_reward_positive CHECK (reward_tickets > 0),
    INDEX idx_referral_referrer_created (referrer_user_id, created_at)
) ENGINE=InnoDB;

-- 과거 Hibernate가 native ENUM으로 만든 환경도 새 참조 유형을 저장할 수 있도록
-- VARCHAR(30)으로 통일한다. 기존 값은 그대로 보존된다.
ALTER TABLE ticket_ledger MODIFY COLUMN ref_type VARCHAR(30) NOT NULL;
