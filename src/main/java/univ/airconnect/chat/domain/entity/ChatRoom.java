package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.ChatRoomType;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", indexes = {
        @Index(name = "idx_chat_room_connection", columnList = "connection_id"),
        @Index(name = "idx_chat_room_pair", columnList = "type,user1_id,user2_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomType type;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "user1_id")
    private Long user1Id;

    @Column(name = "user2_id")
    private Long user2Id;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ChatRoom(String name,
                     ChatRoomType type,
                     Long connectionId,
                     Long user1Id,
                     Long user2Id,
                     String lastMessage,
                     LocalDateTime lastMessageAt) {
        this.name = name;
        this.type = type;
        this.connectionId = connectionId;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public static ChatRoom create(String name, ChatRoomType type) {
        return ChatRoom.builder()
                .name(name)
                .type(type)
                .build();
    }

    public static ChatRoom createPersonal(String name, Long userAId, Long userBId, Long connectionId) {
        Long user1 = Math.min(userAId, userBId);
        Long user2 = Math.max(userAId, userBId);
        return ChatRoom.builder()
                .name(name)
                .type(ChatRoomType.PERSONAL)
                .connectionId(connectionId)
                .user1Id(user1)
                .user2Id(user2)
                .build();
    }

    public void updateLastMessage(String lastMessage, LocalDateTime lastMessageAt) {
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public void bindConnectionIfMissing(Long connectionId) {
        if (connectionId == null || this.connectionId != null) {
            return;
        }
        this.connectionId = connectionId;
        this.updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }
}
