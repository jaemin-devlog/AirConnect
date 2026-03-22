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
    private Long connectionId;
    private LocalDateTime createdAt;
    private String latestMessage;
    private LocalDateTime latestMessageTime;
    private int unreadCount;
    private Long targetUserId;
    private String targetNickname;
    private String targetProfileImage;

    public static ChatRoomResponse from(ChatRoom entity, String latestMessage, LocalDateTime latestMessageTime, int unreadCount) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .connectionId(entity.getConnectionId())
                .createdAt(entity.getCreatedAt())
                .latestMessage(latestMessage)
                .latestMessageTime(latestMessageTime)
                .unreadCount(unreadCount)
                .build();
    }

    public static ChatRoomResponse from(ChatRoom entity,
                                        String latestMessage,
                                        LocalDateTime latestMessageTime,
                                        int unreadCount,
                                        Long targetUserId,
                                        String targetNickname,
                                        String targetProfileImage) {
        return from(entity, entity.getName(), latestMessage, latestMessageTime, unreadCount,
                targetUserId, targetNickname, targetProfileImage);
    }

    public static ChatRoomResponse from(ChatRoom entity,
                                        String displayName,
                                        String latestMessage,
                                        LocalDateTime latestMessageTime,
                                        int unreadCount,
                                        Long targetUserId,
                                        String targetNickname,
                                        String targetProfileImage) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(displayName)
                .type(entity.getType())
                .connectionId(entity.getConnectionId())
                .createdAt(entity.getCreatedAt())
                .latestMessage(latestMessage)
                .latestMessageTime(latestMessageTime)
                .unreadCount(unreadCount)
                .targetUserId(targetUserId)
                .targetNickname(targetNickname)
                .targetProfileImage(targetProfileImage)
                .build();
    }

    // 기존 호환성을 위해 유지
    public static ChatRoomResponse from(ChatRoom entity) {
        return from(entity, null, null, 0);
    }
}
