package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_messages_room_sender_client",
                columnNames = {"room_id", "sender_id", "client_message_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(nullable = false, length = 100)
    private String senderNickname;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // legacy 스키마 호환: 일부 환경에서 message 컬럼이 NOT NULL로 남아 있다.
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String legacyMessage;

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
                        String clientMessageId,
                        String senderNickname,
                        String content,
                        MessageType type,
                        boolean deleted,
                        LocalDateTime deletedAt,
                        LocalDateTime readAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.legacyMessage = content;
        this.type = type;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.readAt = readAt;
        this.createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public static ChatMessage create(Long roomId, Long senderId, String senderNickname, String content, MessageType type) {
        return create(roomId, senderId, senderNickname, content, type, null);
    }

    public static ChatMessage create(Long roomId,
                                     Long senderId,
                                     String senderNickname,
                                     String content,
                                     MessageType type,
                                     String clientMessageId) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
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
        this.deletedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public void markRead() {
        markRead(LocalDateTime.now(java.time.Clock.systemUTC()));
    }

    public void markRead(LocalDateTime readAt) {
        if (this.readAt != null || readAt == null) {
            return;
        }
        this.readAt = readAt;
    }

    public boolean isUnreadTrackable() {
        return !deleted && (type == MessageType.TEXT || type == MessageType.IMAGE);
    }

    public String getDisplayContent() {
        if (this.content != null && !this.content.isBlank()) {
            return this.content;
        }
        return this.legacyMessage;
    }

    @PrePersist
    @PreUpdate
    private void syncLegacyMessageColumn() {
        if (this.content == null && this.legacyMessage != null) {
            this.content = this.legacyMessage;
        }
        if (this.legacyMessage == null && this.content != null) {
            this.legacyMessage = this.content;
        }
    }
}
