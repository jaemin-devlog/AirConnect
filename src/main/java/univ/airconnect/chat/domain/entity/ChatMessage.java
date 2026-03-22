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

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private ChatMessage(Long roomId,
                        Long senderId,
                        String senderNickname,
                        String content,
                        MessageType type,
                        boolean deleted,
                        LocalDateTime deletedAt,
                        LocalDateTime readAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.type = type;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.readAt = readAt;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMessage create(Long roomId, Long senderId, String senderNickname, String content, MessageType type) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .senderNickname(senderNickname)
                .content(content)
                .type(type)
                .deleted(false)
                .build();
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void softDelete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void markRead() {
        if (this.readAt != null) {
            return;
        }
        this.readAt = LocalDateTime.now();
    }
}
