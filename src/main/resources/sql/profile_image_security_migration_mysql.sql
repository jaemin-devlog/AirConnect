-- AirConnect profile image security hardening migration (MySQL 8.x)
-- 목적:
-- 1) profileImagePath 역조회 성능 확보 (이미지 접근 시 owner/status 검증 용도)

CREATE INDEX idx_user_profiles_profile_image_path
    ON user_profiles (profile_image_path(191));
