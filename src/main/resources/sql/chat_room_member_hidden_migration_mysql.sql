-- 채팅방 멤버 숨김(차단 시 사용자별 비노출) 컬럼 추가
ALTER TABLE chat_room_members
    ADD COLUMN hidden_at DATETIME NULL,
    ADD COLUMN hidden_reason VARCHAR(40) NULL;

CREATE INDEX idx_chat_room_members_user_hidden
    ON chat_room_members (user_id, hidden_at);
