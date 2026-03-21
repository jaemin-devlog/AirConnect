package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.matching.domain.ConnectionStatus;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "matching_connections",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConnectionStatus status;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    private MatchingConnection(Long user1Id, Long user2Id, Long requesterId, Long chatRoomId, ConnectionStatus status, LocalDateTime connectedAt) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.requesterId = requesterId;
        this.chatRoomId = chatRoomId;
        this.status = status;
        this.connectedAt = connectedAt;
    }

    public static MatchingConnection createPending(Long requesterUserId, Long targetUserId) {
        Long user1 = Math.min(requesterUserId, targetUserId);
        Long user2 = Math.max(requesterUserId, targetUserId);
        return new MatchingConnection(user1, user2, requesterUserId, null, ConnectionStatus.PENDING, LocalDateTime.now());
    }

    public void accept(Long chatRoomId) {
        this.status = ConnectionStatus.ACCEPTED;
        this.chatRoomId = chatRoomId;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ConnectionStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void resetToPending() {
        this.status = ConnectionStatus.PENDING;
        this.respondedAt = null;
        this.connectedAt = LocalDateTime.now();
    }

    public void reopenAsPending(Long requesterUserId) {
        this.status = ConnectionStatus.PENDING;
        this.requesterId = requesterUserId;
        this.chatRoomId = null;
        this.respondedAt = null;
        this.connectedAt = LocalDateTime.now();
    }

    public boolean isParticipant(Long userId) {
        return Objects.equals(this.user1Id, userId) || Objects.equals(this.user2Id, userId);
    }

    public boolean isRequester(Long userId) {
        return Objects.equals(this.requesterId, userId);
    }

    public boolean isReceiver(Long userId) {
        return isParticipant(userId) && !isRequester(userId);
    }

    public Long getOtherUserId(Long userId) {
        return userId.equals(user1Id) ? user2Id : user1Id;
    }
}
