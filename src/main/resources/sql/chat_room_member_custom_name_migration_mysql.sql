-- 채팅방 이름은 참여자별 별칭으로 저장한다. 개인 채팅에서도 상대방에게 영향을 주지 않는다.
ALTER TABLE chat_room_members
    ADD COLUMN custom_name VARCHAR(100) NULL AFTER user_id;
