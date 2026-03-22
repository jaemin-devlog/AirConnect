-- 1:1 chat 확장 마이그레이션
-- chat_rooms: connection/pair/lastMessage
ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS connection_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS user1_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS user2_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS last_message VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS last_message_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 기존 PERSONAL 방에 대해 멤버 기준 user1/user2를 채운다.
UPDATE chat_rooms cr
JOIN (
    SELECT crm.chat_room_id,
           MIN(crm.user_id) AS user1_id,
           MAX(crm.user_id) AS user2_id
    FROM chat_room_members crm
    GROUP BY crm.chat_room_id
) pair ON pair.chat_room_id = cr.id
SET cr.user1_id = pair.user1_id,
    cr.user2_id = pair.user2_id
WHERE cr.type = 'PERSONAL'
  AND (cr.user1_id IS NULL OR cr.user2_id IS NULL);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_rooms_connection_id ON chat_rooms(connection_id);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_pair ON chat_rooms(type, user1_id, user2_id);

-- chat_messages: content/read/delete 필드
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS content TEXT NULL,
    ADD COLUMN IF NOT EXISTS is_deleted BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS read_at DATETIME NULL;

-- 기존 message 데이터를 content로 이관
UPDATE chat_messages
SET content = message
WHERE content IS NULL;

ALTER TABLE chat_messages
    MODIFY COLUMN content TEXT NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created ON chat_messages(room_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chat_messages_sender ON chat_messages(sender_id);

