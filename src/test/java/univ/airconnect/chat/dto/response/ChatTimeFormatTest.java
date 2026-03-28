package univ.airconnect.chat.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ChatTimeFormatTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void chatMessageResponseTimesAreOffsetAndMatchPattern() throws Exception {
        // LocalDateTime is treated as UTC in response mapper
        LocalDateTime created = LocalDateTime.of(2026, 3, 29, 3, 21, 45, 123456000);

        ChatMessage msg = ChatMessage.create(1L, 2L, "nick", "hello", MessageType.TEXT);
        // createdAt 필드를 직접 주입할 수 없어서, response의 포맷 매핑만 검증하기 위해
        // OffsetDateTime 값을 가진 response를 직접 구성한다.
        OffsetDateTime createdAt = created.atOffset(ZoneOffset.UTC);

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(10L)
                .roomId(1L)
                .chatRoomId(1L)
                .senderId(2L)
                .senderNickname("nick")
                .content("hello")
                .message("hello")
                .messageType(MessageType.TEXT)
                .type(MessageType.TEXT)
                .deleted(false)
                .sentAt(createdAt)
                .createdAt(createdAt)
                .readAt(null)
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"createdAt\":\"2026-03-29T03:21:45.123456Z\"");
        assertThat(json).contains("\"sentAt\":\"2026-03-29T03:21:45.123456Z\"");
        assertThat(json).doesNotContain("\"createdAt\":\"2026-03-29T03:21:45.123456\"");
    }

    @Test
    void offsetIsRequiredWhenDeserializingOffsetDateTime() {
        // Offset 없는 문자열은 OffsetDateTime으로 역직렬화 시 실패해야 한다.
        String payload = "{\"createdAt\":\"2026-03-29T03:21:45.123456\"}";
        try {
            objectMapper.readTree(payload);
        } catch (Exception ignored) {
            // readTree 자체는 문자열로 읽을 수 있으므로 여기서 실패하진 않음
        }

        // 강제 역직렬화 검증
        String wrapper = "{\"id\":1,\"roomId\":1,\"chatRoomId\":1,\"senderId\":1,\"senderNickname\":\"n\",\"content\":\"c\",\"message\":\"c\",\"messageType\":\"TEXT\",\"type\":\"TEXT\",\"deleted\":false,\"createdAt\":\"2026-03-29T03:21:45.123456\"}";
        try {
            objectMapper.readValue(wrapper, ChatMessageResponse.class);
            throw new AssertionError("Expected deserialization to fail due to missing offset.");
        } catch (Exception ex) {
            // 오프셋이 없으면 OffsetDateTime 파싱이 실패해야 한다.
            assertThat(ex.getMessage())
                    .contains("Cannot deserialize value of type")
                    .contains("OffsetDateTime");
        }
    }
}


