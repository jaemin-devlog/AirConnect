package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 100)
    private String senderNickname;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatMessage(Long roomId, Long senderId, String senderNickname, String message, MessageType type) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.message = message;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMessage create(Long roomId, Long senderId, String senderNickname, String message, MessageType type) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .senderNickname(senderNickname)
                .message(message)
                .type(type)
                .build();
    }
}
