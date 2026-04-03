package univ.airconnect.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.analytics.domain.AnalyticsEventSource;
import univ.airconnect.analytics.domain.AnalyticsEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "analytics_events",
        indexes = {
                @Index(name = "idx_analytics_event_user_date", columnList = "user_id,event_date"),
                @Index(name = "idx_analytics_event_type_date", columnList = "type,event_date"),
                @Index(name = "idx_analytics_event_occurred_at", columnList = "occurred_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalyticsEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalyticsEventSource source;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "screen_name", length = 100)
    private String screenName;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AnalyticsEvent(Long userId,
                           AnalyticsEventType type,
                           AnalyticsEventSource source,
                           String sessionId,
                           String deviceId,
                           String screenName,
                           LocalDateTime occurredAt,
                           String payloadJson) {
        this.userId = userId;
        this.type = type;
        this.source = source;
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.screenName = screenName;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
        this.eventDate = this.occurredAt.toLocalDate();
        this.payloadJson = (payloadJson == null || payloadJson.isBlank()) ? "{}" : payloadJson;
        this.createdAt = LocalDateTime.now();
    }

    public static AnalyticsEvent client(Long userId,
                                        AnalyticsEventType type,
                                        String sessionId,
                                        String deviceId,
                                        String screenName,
                                        LocalDateTime occurredAt,
                                        String payloadJson) {
        return AnalyticsEvent.builder()
                .userId(userId)
                .type(type)
                .source(AnalyticsEventSource.CLIENT)
                .sessionId(sessionId)
                .deviceId(deviceId)
                .screenName(screenName)
                .occurredAt(occurredAt)
                .payloadJson(payloadJson)
                .build();
    }

    public static AnalyticsEvent server(Long userId,
                                        AnalyticsEventType type,
                                        LocalDateTime occurredAt,
                                        String payloadJson) {
        return AnalyticsEvent.builder()
                .userId(userId)
                .type(type)
                .source(AnalyticsEventSource.SERVER)
                .occurredAt(occurredAt)
                .payloadJson(payloadJson)
                .build();
    }
}
