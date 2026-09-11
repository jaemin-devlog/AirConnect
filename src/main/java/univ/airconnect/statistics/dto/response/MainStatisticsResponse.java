package univ.airconnect.statistics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MainStatisticsResponse {

    private final long totalRegisteredUsers;
    private final long dailyActiveUsers;
    private final int onlineUserCount;
    private final GenderRatio genderRatio;
    private final long totalMatchSuccessCount;
    private final LocalDateTime generatedAt;

    @Getter
    @Builder
    public static class GenderRatio {
        private final long maleUsers;
        private final long femaleUsers;
        private final long unknownUsers;
        private final int malePercentage;
        private final int femalePercentage;
    }

}
