package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AdminChatInspectionRetentionWorker {

    private static final int RETENTION_DAYS = 90;
    private final AdminAuditLogService adminAuditLogService;

    @Scheduled(cron = "${admin.chat-inspection.retention-cron:0 30 3 * * *}")
    public void deleteExpiredLogs() {
        adminAuditLogService.deleteExpiredChatInspectionLogs(
                // AdminAuditLog.createdAt uses the JVM's local wall clock.
                LocalDateTime.now().minusDays(RETENTION_DAYS)
        );
    }
}
