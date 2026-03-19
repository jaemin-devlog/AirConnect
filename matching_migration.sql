-- matching_connections 테이블 스키마 변경
-- status, requester_id, responded_at 컬럼 추가

ALTER TABLE matching_connections ADD COLUMN requester_id BIGINT NOT NULL AFTER user2_id;
ALTER TABLE matching_connections ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER requester_id;
ALTER TABLE matching_connections MODIFY COLUMN chat_room_id BIGINT;
ALTER TABLE matching_connections ADD COLUMN responded_at DATETIME AFTER connected_at;

-- 기존 데이터 마이그레이션 (만약 데이터가 있다면)
UPDATE matching_connections SET status = 'ACCEPTED' WHERE chat_room_id IS NOT NULL;

