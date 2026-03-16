package univ.airconnect.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomResponse {
    private Long id;
    private String name;
    private ChatRoomType type;
    private LocalDateTime createdAt;
    private String latestMessage;
    private LocalDateTime latestMessageTime;
    private int unreadCount;

    public static ChatRoomResponse from(ChatRoom entity, String latestMessage, LocalDateTime latestMessageTime, int unreadCount) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .latestMessage(latestMessage)
                .latestMessageTime(latestMessageTime)
                .unreadCount(unreadCount)
                .build();
    }

    // 기존 호환성을 위해 유지
    public static ChatRoomResponse from(ChatRoom entity) {
        return from(entity, null, null, 0);
    }
}
