package univ.airconnect.chat.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Clock;
import java.time.LocalDateTime;

/** Durable delivery intent, committed in the same transaction as the chat change. */
@Entity
@Table(name = "chat_delivery_events", indexes = {
        @Index(name = "idx_chat_delivery_due", columnList = "next_attempt_at,id"),
        @Index(name = "idx_chat_delivery_lane", columnList = "lane,id")
})
@Getter
@NoArgsConstructor
public class ChatDeliveryEvent {
    public enum Kind { MESSAGE, ROOM_LIST, NOTIFICATION }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Kind kind;
    @Column(nullable = false) private Long roomId;
    private Long messageId;
    private Long userId;
    @Column(nullable = false, length = 100) private String lane;
    @Column(nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(nullable = false) private LocalDateTime nextAttemptAt;
    @Column(nullable = false) private int attempts;

    public static ChatDeliveryEvent create(Kind kind, Long roomId, Long messageId, Long userId, String payload) {
        var event = new ChatDeliveryEvent();
        event.kind = kind;
        event.roomId = roomId;
        event.messageId = messageId;
        event.userId = userId;
        event.lane = kind + ":" + roomId + ":" + (userId == null ? "all" : userId);
        event.payloadJson = payload;
        event.nextAttemptAt = LocalDateTime.now(Clock.systemUTC());
        return event;
    }

    public void defer() {
        attempts++;
        nextAttemptAt = LocalDateTime.now(Clock.systemUTC()).plusSeconds(Math.min(60, attempts * 2L));
    }
}
