package univ.airconnect.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String eventType;
    private Long id;
    private Long messageId;
    private Long roomId;
    private Long chatRoomId;
    private Long senderId;
    private String senderNickname;
    private String senderProfileImage;
    private String content;
    private String message;
    private MessageType messageType;
    private MessageType type;
    private boolean deleted;
    private Integer unreadCount;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime readAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime createdAt;

    private static final String EVENT_MESSAGE = "MESSAGE";
    private static final String EVENT_READ_RECEIPT = "READ_RECEIPT";
    private static final String DELETED_CONTENT = "삭제된 메시지입니다.";
    private static final ZoneOffset RESPONSE_OFFSET = ZoneOffset.UTC;

    public static ChatMessageResponse from(ChatMessage entity, String profileImage) {
        return from(entity, profileImage, 0);
    }

    public static ChatMessageResponse from(ChatMessage entity, String profileImage, Integer unreadCount) {
        String renderedContent = entity.isDeleted() ? DELETED_CONTENT : entity.getDisplayContent();
        return ChatMessageResponse.builder()
                .eventType(EVENT_MESSAGE)
                .id(entity.getId())
                .messageId(entity.getId())
                .roomId(entity.getRoomId())
                .chatRoomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .senderNickname(entity.getSenderNickname())
                .senderProfileImage(profileImage)
                .content(renderedContent)
                .message(renderedContent)
                .messageType(entity.getType())
                .type(entity.getType())
                .deleted(entity.isDeleted())
                .unreadCount(unreadCount != null ? unreadCount : 0)
                .readAt(toOffset(entity.getReadAt()))
                .sentAt(toOffset(entity.getCreatedAt()))
                .createdAt(toOffset(entity.getCreatedAt()))
                .build();
    }

    public static ChatMessageResponse readReceipt(Long roomId, Long messageId, LocalDateTime readAt, Integer unreadCount) {
        OffsetDateTime offsetReadAt = toOffset(readAt);
        return ChatMessageResponse.builder()
                .eventType(EVENT_READ_RECEIPT)
                .id(messageId)
                .messageId(messageId)
                .roomId(roomId)
                .chatRoomId(roomId)
                .unreadCount(unreadCount != null ? unreadCount : 0)
                .readAt(offsetReadAt)
                .sentAt(offsetReadAt)
                .createdAt(offsetReadAt)
                .build();
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        // DB/서버 기준 시간을 UTC로 간주하여 OffsetDateTime(오프셋 포함)으로 통일한다.
        return value.atOffset(RESPONSE_OFFSET);
    }
}
