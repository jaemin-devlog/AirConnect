package univ.airconnect.analytics.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.analytics.domain.AnalyticsEventType;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTrackItemRequest {

    @NotNull
    private AnalyticsEventType type;

    private LocalDateTime occurredAt;

    private String sessionId;

    private String deviceId;

    private String screenName;

    private Map<String, Object> payload;
}
