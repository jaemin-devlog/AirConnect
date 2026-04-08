-- AirConnect email authentication migration (MySQL 8.x)
-- 목적:
-- 1) 이메일 계정 비밀번호 해시 저장 컬럼 추가
-- 2) 기존 데이터 호환(기존 소셜 계정은 NULL 유지)

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NULL AFTER email;

