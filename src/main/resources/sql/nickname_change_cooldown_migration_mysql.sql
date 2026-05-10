-- AirConnect nickname change cooldown migration (MySQL 8.x)
-- 목적:
-- 1) 마지막 닉네임 변경 시각 저장
-- 2) 닉네임 변경 14일 쿨다운 적용

ALTER TABLE users
    ADD COLUMN last_nickname_changed_at DATETIME NULL AFTER nickname;
