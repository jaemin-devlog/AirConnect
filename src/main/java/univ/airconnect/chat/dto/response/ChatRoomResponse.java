package univ.airconnect.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class ChatRoomResponse {
    private Long id;
    private String name;
    private ChatRoomType type;
    private Long connectionId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime createdAt;
    private String latestMessage;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime latestMessageTime;
    private int unreadCount;
    private Long targetUserId;
    private String targetNickname;
    private String targetProfileImage;
    private ChatParticipantDetailResponse targetProfile;

    public static ChatRoomResponse from(ChatRoom entity, String latestMessage, LocalDateTime latestMessageTime, int unreadCount) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .connectionId(entity.getConnectionId())
                .createdAt(toOffset(entity.getCreatedAt()))
                .latestMessage(latestMessage)
                .latestMessageTime(toOffset(latestMessageTime))
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
                targetUserId, targetNickname, targetProfileImage, null);
    }

    public static ChatRoomResponse from(ChatRoom entity,
                                        String displayName,
                                        String latestMessage,
                                        LocalDateTime latestMessageTime,
                                        int unreadCount,
                                        Long targetUserId,
                                        String targetNickname,
                                        String targetProfileImage) {
        return from(entity, displayName, latestMessage, latestMessageTime, unreadCount,
                targetUserId, targetNickname, targetProfileImage, null);
    }

    public static ChatRoomResponse from(ChatRoom entity,
                                        String displayName,
                                        String latestMessage,
                                        LocalDateTime latestMessageTime,
                                        int unreadCount,
                                        Long targetUserId,
                                        String targetNickname,
                                        String targetProfileImage,
                                        ChatParticipantDetailResponse targetProfile) {
        return ChatRoomResponse.builder()
                .id(entity.getId())
                .name(displayName)
                .type(entity.getType())
                .connectionId(entity.getConnectionId())
                .createdAt(toOffset(entity.getCreatedAt()))
                .latestMessage(latestMessage)
                .latestMessageTime(toOffset(latestMessageTime))
                .unreadCount(unreadCount)
                .targetUserId(targetUserId)
                .targetNickname(targetNickname)
                .targetProfileImage(targetProfileImage)
                .targetProfile(targetProfile)
                .build();
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        // DB/서버 저장 시간을 UTC로 간주해 OffsetDateTime으로 통일한다.
        return value.atOffset(ZoneOffset.UTC);
    }

    // 기존 호환성을 위해 유지
    public static ChatRoomResponse from(ChatRoom entity) {
        return from(entity, null, null, 0);
    }
}
