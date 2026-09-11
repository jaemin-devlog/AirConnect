-- 초대 코드 전용 그룹매칭 전환. 기존 서버를 중지한 후, 새 서버 시작 전에 실행한다.
-- 기존 메시지/팀/준비 기록은 삭제하지 않는다. MySQL 8 기준, 재실행 가능.
-- temp_chat_room_id는 과거 기록 조회용으로 보존하며 새 팀에는 NULL을 저장한다.
ALTER TABLE matching_temporary_team_rooms
    MODIFY COLUMN temp_chat_room_id BIGINT NULL;

-- 기존 VARCHAR 또는 ENUM 저장소에서 신규 TEAM_MATCHING_STOPPED 값을 허용한다.
-- 기존 문자열 값은 보존되며 배포 후 Hibernate update도 새 enum 전체를 인식한다.
ALTER TABLE notifications MODIFY COLUMN type VARCHAR(50) NOT NULL;

START TRANSACTION;

-- 예전 임시 채팅은 채팅 목록 및 기존 채팅 접근 권한에서 제외한다.
-- 최종 그룹 채팅과 1:1 채팅에는 적용되지 않는다.
UPDATE chat_room_members cm
JOIN matching_temporary_team_rooms t ON t.temp_chat_room_id = cm.chat_room_id
SET cm.hidden_at = COALESCE(cm.hidden_at, UTC_TIMESTAMP()),
    cm.hidden_reason = CASE WHEN cm.hidden_at IS NULL OR cm.hidden_reason IS NULL
                            THEN 'GROUP_MATCHING_TEMP_RETIRED' ELSE cm.hidden_reason END;

UPDATE matching_temporary_team_rooms
SET status = 'OPEN', updated_at = CURRENT_TIMESTAMP
WHERE status = 'READY_CHECK';

UPDATE matching_temporary_team_rooms
SET visibility = 'PRIVATE',
    opponent_gender_filter = CASE WHEN team_gender = 'M' THEN 'F' ELSE 'M' END
WHERE status IN ('OPEN', 'QUEUE_WAITING', 'MATCHED');

COMMIT;

-- 모든 값이 0이어야 한다.
SELECT COUNT(*) AS visible_legacy_temporary_chat_members
FROM chat_room_members cm
JOIN matching_temporary_team_rooms t ON t.temp_chat_room_id = cm.chat_room_id
WHERE cm.hidden_at IS NULL;
SELECT COUNT(*) AS legacy_ready_teams
FROM matching_temporary_team_rooms WHERE status = 'READY_CHECK';
