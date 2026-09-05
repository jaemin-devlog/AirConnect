-- Disposable IsolatedReportMySqlServer database ONLY. Never run against production.
USE airconnect_admin_verify;
INSERT INTO chat_messages (room_id, sender_id, sender_nickname, content, message, type, created_at, is_deleted)
WITH RECURSIVE sequence_rows AS (
    SELECT 1 AS n UNION ALL SELECT n + 1 FROM sequence_rows WHERE n < 60
)
SELECT r.id, 3, '가상 대상 회원', CONCAT('스크롤 검증용 가상 메시지 ', n),
       CONCAT('스크롤 검증용 가상 메시지 ', n), 'TEXT',
       UTC_TIMESTAMP() - INTERVAL (60 - n) SECOND, 0
FROM sequence_rows CROSS JOIN chat_rooms r
WHERE r.name = '격리 검증 대화방';
