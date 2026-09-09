-- Manual migration: apply once before deploying Chat Phase 1B.
-- Existing rows remain compatible because client_message_id is nullable.
-- MySQL permits multiple NULL values in a UNIQUE constraint, so requests from
-- older app versions that omit clientMessageId keep their existing behavior.
ALTER TABLE chat_messages
    ADD COLUMN client_message_id VARCHAR(64) NULL AFTER sender_id;

ALTER TABLE chat_messages
    ADD CONSTRAINT uk_chat_messages_room_sender_client
        UNIQUE (room_id, sender_id, client_message_id);
