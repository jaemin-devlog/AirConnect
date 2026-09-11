package univ.airconnect.statistics.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record RealtimeMainStatisticsResponse(
        long totalRegisteredUsers,
        long totalMatchSuccessCount,
        long last24HoursActiveUserCount,
        List<TopDepartment> topDepartments,
        LocalDateTime updatedAt
) {
    public record TopDepartment(
            int rank,
            Long departmentId,
            String deptName,
            long requestCount
    ) {
    }
}
