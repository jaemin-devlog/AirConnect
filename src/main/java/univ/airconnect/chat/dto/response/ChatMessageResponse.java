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
    private Long senderId;
    private String senderNickname;
    private String senderProfileImage;
    private String message;
    private MessageType type;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage entity, String profileImage) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .senderNickname(entity.getSenderNickname())
                .senderProfileImage(profileImage)
                .message(entity.getMessage())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
