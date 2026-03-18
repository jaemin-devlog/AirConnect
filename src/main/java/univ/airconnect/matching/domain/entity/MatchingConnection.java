package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    private MatchingConnection(Long user1Id, Long user2Id, Long chatRoomId, LocalDateTime connectedAt) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.chatRoomId = chatRoomId;
        this.connectedAt = connectedAt;
    }

    public static MatchingConnection create(Long userAId, Long userBId, Long chatRoomId) {
        Long user1 = Math.min(userAId, userBId);
        Long user2 = Math.max(userAId, userBId);
        return new MatchingConnection(user1, user2, chatRoomId, LocalDateTime.now());
    }
}


