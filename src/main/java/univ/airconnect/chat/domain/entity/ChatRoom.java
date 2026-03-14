package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.ChatRoomType;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatRoom(String name, ChatRoomType type) {
        this.name = name;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatRoom create(String name, ChatRoomType type) {
        return ChatRoom.builder()
                .name(name)
                .type(type)
                .build();
    }
}
