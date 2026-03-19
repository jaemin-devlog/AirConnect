-- 🔍 더미 데이터 확인 쿼리

-- 1. 기본 데이터 확인
SELECT
  (SELECT COUNT(*) FROM users) as 총_사용자_수,
  (SELECT COUNT(*) FROM user_profiles) as 총_프로필_수,
  (SELECT COUNT(*) FROM matching_queue_entries) as 큐_진입_수;

-- 2. 남/여 분류 확인
SELECT gender, COUNT(*) as 인원
FROM user_profiles
GROUP BY gender;

-- 3. 사용자별 프로필 상세 (첫 5명)
SELECT
  u.id,
  u.name,
  u.nickname,
  u.tickets,
  up.gender,
  up.height,
  up.mbti
FROM users u
LEFT JOIN user_profiles up ON u.id = up.user_id
LIMIT 5;

-- 4. 매칭 큐 상태 확인
SELECT * FROM matching_queue_entries;

-- 5. 각 성별별 활성 큐 확인
SELECT
  up.gender,
  COUNT(mq.id) as 활성_큐_수
FROM user_profiles up
LEFT JOIN matching_queue_entries mq ON up.user_id = mq.user_id AND mq.active = true
GROUP BY up.gender;

