package univ.airconnect.analytics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalyticsTrackResponse {

    private final int savedCount;
    private final LocalDateTime trackedAt;
}
