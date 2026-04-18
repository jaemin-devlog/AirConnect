package univ.airconnect.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomListUpdateResponse {

    private static final String EVENT_ROOM_LIST_UPDATE = "ROOM_LIST_UPDATE";

    private String eventType;
    private Long userId;
    private Long roomId;
    private String latestMessage;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime latestMessageTime;
    private Integer unreadCount;

    public static ChatRoomListUpdateResponse of(Long userId,
                                                Long roomId,
                                                String latestMessage,
                                                LocalDateTime latestMessageTime,
                                                Integer unreadCount) {
        return ChatRoomListUpdateResponse.builder()
                .eventType(EVENT_ROOM_LIST_UPDATE)
                .userId(userId)
                .roomId(roomId)
                .latestMessage(latestMessage)
                .latestMessageTime(toOffset(latestMessageTime))
                .unreadCount(unreadCount != null ? unreadCount : 0)
                .build();
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC);
    }
}
