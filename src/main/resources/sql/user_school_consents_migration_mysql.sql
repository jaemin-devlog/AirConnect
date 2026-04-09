-- AirConnect school verification consent migration (MySQL 8.x)
-- 목적:
-- 1) 학교 인증 진입 전 동의 항목별 체크 결과 저장
-- 2) 사용자당 최신 동의 상태 단건 관리

CREATE TABLE IF NOT EXISTS user_school_consents (
    user_id BIGINT NOT NULL,
    adult_and_college_confirmed TINYINT(1) NOT NULL,
    terms_of_service_agreed TINYINT(1) NOT NULL,
    privacy_collection_agreed TINYINT(1) NOT NULL,
    profile_disclosure_agreed TINYINT(1) NOT NULL,
    marketing_agreed TINYINT(1) NOT NULL,
    required_consents_agreed TINYINT(1) NOT NULL,
    all_consents_agreed TINYINT(1) NOT NULL,
    agreed_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_school_consents_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

