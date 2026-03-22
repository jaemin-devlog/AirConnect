package univ.airconnect.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
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
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    private static final String DELETED_CONTENT = "삭제된 메시지입니다.";

    public static ChatMessageResponse from(ChatMessage entity, String profileImage) {
        String renderedContent = entity.isDeleted() ? DELETED_CONTENT : entity.getContent();
        return ChatMessageResponse.builder()
                .id(entity.getId())
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
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
