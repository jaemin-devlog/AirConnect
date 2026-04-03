package univ.airconnect.statistics.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MainStatisticsResponse {

    private final long totalRegisteredUsers;
    private final long dailyActiveUsers;
    private final GenderRatio genderRatio;
    private final long totalMatchSuccessCount;
    private final List<DepartmentRanking> topRequestedDepartments;
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

    @Getter
    @Builder
    public static class DepartmentRanking {
        private final int rank;
        private final String deptName;
        private final long requestCount;
    }
}
