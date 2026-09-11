package univ.airconnect.statistics.dto.response;

import java.time.LocalDateTime;

public record RealtimeMainStatisticsResponse(
        long totalRegisteredUsers,
        long totalMatchSuccessCount,
        int onlineUserCount,
        TopDepartment topDepartment,
        LocalDateTime updatedAt
) {
    public record TopDepartment(
            Long departmentId,
            String deptName,
            long requestCount
    ) {
    }
}
