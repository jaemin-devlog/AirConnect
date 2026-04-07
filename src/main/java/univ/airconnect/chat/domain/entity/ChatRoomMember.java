package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.user.domain.entity.User;

import java.time.LocalDateTime;

/**
 * 채팅방 참여자 정보를 관리하는 엔티티.
 * 이를 통해 특정 사용자가 해당 방의 멤버인지 권한 검증이 가능해진다.
 */
@Entity
@Table(name = "chat_room_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column
    private Long lastReadMessageId;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_reason", length = 40)
    private String hiddenReason;

    private ChatRoomMember(ChatRoom chatRoom, User user) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.joinedAt = LocalDateTime.now();
    }

    public void updateLastReadMessageId(Long messageId) {
        this.lastReadMessageId = messageId;
    }

    public void hide(String reason) {
        if (this.hiddenAt != null) {
            return;
        }
        this.hiddenAt = LocalDateTime.now();
        this.hiddenReason = reason;
    }

    public void unhide() {
        this.hiddenAt = null;
        this.hiddenReason = null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public static ChatRoomMember create(ChatRoom chatRoom, User user) {
        return new ChatRoomMember(chatRoom, user);
    }
}
