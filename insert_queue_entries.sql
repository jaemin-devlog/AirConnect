-- 📌 매칭 큐에 모든 더미 데이터 자동 추가
-- 이 쿼리를 실행하면 남녀 20명 모두가 매칭 대기열에 진입합니다

INSERT INTO matching_queue_entries (user_id, active, entered_at, updated_at)
SELECT
  id,
  true,
  NOW(),
  NOW()
FROM users
WHERE id NOT IN (SELECT user_id FROM matching_queue_entries);

-- 확인
SELECT
  (SELECT COUNT(*) FROM matching_queue_entries) as 큐_진입_수,
  (SELECT COUNT(*) FROM matching_queue_entries WHERE active = true) as 활성_큐_수;

