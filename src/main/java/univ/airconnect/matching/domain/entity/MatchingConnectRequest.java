package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "matching_connect_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_matching_connect_user_key",
                columnNames = {"user_id", "request_key"}
        ),
        indexes = @Index(name = "idx_matching_connect_created", columnList = "created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingConnectRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "already_connected", nullable = false)
    private boolean alreadyConnected;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static MatchingConnectRequest create(Long userId,
                                                String requestKey,
                                                Long targetUserId,
                                                Long connectionId,
                                                Long chatRoomId,
                                                boolean alreadyConnected) {
        MatchingConnectRequest request = new MatchingConnectRequest();
        request.userId = userId;
        request.requestKey = requestKey;
        request.targetUserId = targetUserId;
        request.connectionId = connectionId;
        request.chatRoomId = chatRoomId;
        request.alreadyConnected = alreadyConnected;
        request.createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
        return request;
    }
}
