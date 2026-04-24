package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext()
            );
        }
    }

    public record UserSummary(
            Long userId,
            String provider,
            String socialId,
            String email,
            String schoolName,
            String deptName,
            String nickname,
            UserStatus status,
            OnboardingStatus onboardingStatus,
            Gender gender,
            Integer tickets,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            boolean matchingRestricted
    ) {
    }

    public record UserDetail(
            Long userId,
            String provider,
            String socialId,
            String email,
            String schoolName,
            String deptName,
            String nickname,
            String name,
            Integer studentNum,
            UserStatus status,
            OnboardingStatus onboardingStatus,
            Gender gender,
            Integer tickets,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            LocalDateTime deletedAt,
            LocalDateTime suspendedUntil,
            LocalDateTime restrictedAt,
            LocalDateTime restrictedUntil,
            String restrictedReason,
            long openReportCount
    ) {
    }

    public record MatchingRecord(
            Long connectionId,
            ConnectionStatus status,
            Long requesterUserId,
            Long user1Id,
            String user1Nickname,
            Long user2Id,
            String user2Nickname,
            Long chatRoomId,
            LocalDateTime connectedAt,
            LocalDateTime respondedAt
    ) {
    }

    public record ReportRecord(
            Long reportId,
            Long reporterUserId,
            String reporterNickname,
            Long reportedUserId,
            String reportedNickname,
            ReportReasonCode reason,
            String detail,
            ReportStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TicketBalance(
            Long userId,
            Integer currentTickets
    ) {
    }

    public record TicketLedgerItem(
            Long ledgerId,
            Integer changeAmount,
            Integer beforeAmount,
            Integer afterAmount,
            String reason,
            String refType,
            String refId,
            LocalDateTime createdAt
    ) {
    }

    public record StatisticsOverview(
            long totalRegisteredUsers,
            long dailyActiveUsers,
            long totalMatchSuccessCount,
            long grantedTickets,
            long consumedTickets,
            long openReports,
            LocalDateTime generatedAt
    ) {
    }

    public record NoticeBroadcastResult(
            int recipients,
            String title
    ) {
    }
}
