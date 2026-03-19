-- ✅ 데이터베이스 초기화 방법

-- ============================================
-- 방법 1: 테이블 데이터만 삭제 (추천)
-- ============================================
-- 모든 테이블의 데이터를 삭제합니다 (테이블 구조는 유지)

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE matching_connections;
TRUNCATE TABLE matching_exposures;
TRUNCATE TABLE matching_queue_entries;
TRUNCATE TABLE chat_messages;
TRUNCATE TABLE chat_room_members;
TRUNCATE TABLE chat_rooms;
TRUNCATE TABLE user_profiles;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

-- 확인
SELECT
  (SELECT COUNT(*) FROM users) as users_count,
  (SELECT COUNT(*) FROM user_profiles) as profiles_count,
  (SELECT COUNT(*) FROM matching_queue_entries) as queue_count;


-- ============================================
-- 방법 2: 모든 테이블 삭제 (재생성 필요)
-- ============================================
-- 주의: 이 방법은 테이블 구조도 삭제합니다
-- 실행 후 Spring Boot를 rebuild해야 Hibernate가 다시 생성합니다

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS matching_connections;
DROP TABLE IF EXISTS matching_exposures;
DROP TABLE IF EXISTS matching_queue_entries;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_room_members;
DROP TABLE IF EXISTS chat_rooms;
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- 확인
SHOW TABLES;


-- ============================================
-- 방법 3: 데이터베이스 자체 삭제 (완전 초기화)
-- ============================================
-- 위험! 데이터베이스 전체를 삭제합니다
-- 실행 후 Spring Boot를 rebuild하면 자동으로 새로운 데이터베이스 생성됩니다

DROP DATABASE IF EXISTS airconnect;
CREATE DATABASE airconnect;
USE airconnect;

-- 이제 Spring Boot rebuild 시 모든 테이블을 처음부터 생성합니다

