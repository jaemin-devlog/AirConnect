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

    public static ChatRoomResponse from(ChatRoom entity) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
