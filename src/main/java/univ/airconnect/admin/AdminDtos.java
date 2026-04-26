package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import univ.airconnect.analytics.domain.AnalyticsEventSource;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
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
            UserRole role,
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
            UserRole role,
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
            long openReportCount,
            List<PurchaseHistoryItem> purchaseHistories,
            List<SentRequestHistoryItem> sentRequestHistories,
            List<TicketUsageHistoryItem> ticketUsageHistories,
            List<ApiUsageHistoryItem> apiUsageHistories
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

    public record PurchaseHistoryItem(
            Long orderId,
            IapStore store,
            String productId,
            IapOrderStatus status,
            Integer grantedTickets,
            Integer beforeTickets,
            Integer afterTickets,
            String transactionId,
            String orderKey,
            LocalDateTime processedAt,
            LocalDateTime createdAt
    ) {
    }

    public record SentRequestHistoryItem(
            Long connectionId,
            ConnectionStatus status,
            Long targetUserId,
            String targetNickname,
            Long chatRoomId,
            LocalDateTime connectedAt,
            LocalDateTime respondedAt
    ) {
    }

    public record TicketUsageHistoryItem(
            Long ledgerId,
            Integer usedAmount,
            Integer beforeAmount,
            Integer afterAmount,
            String reason,
            String refType,
            String refId,
            LocalDateTime createdAt
    ) {
    }

    public record ApiUsageHistoryItem(
            Long eventId,
            AnalyticsEventType type,
            AnalyticsEventSource source,
            String screenName,
            String sessionId,
            String deviceId,
            String payloadJson,
            LocalDateTime occurredAt
    ) {
    }

    public record StatisticsOverview(
            long totalRegisteredUsers,
            long dailyActiveUsers,
            GenderRatio genderRatio,
            long totalMatchSuccessCount,
            List<DepartmentRanking> topRequestedDepartments,
            long grantedTickets,
            long consumedTickets,
            long openReports,
            LocalDateTime generatedAt
    ) {
    }

    public record GenderRatio(
            long maleUsers,
            long femaleUsers,
            long unknownUsers,
            int malePercentage,
            int femalePercentage
    ) {
        public static GenderRatio from(univ.airconnect.statistics.dto.response.MainStatisticsResponse.GenderRatio value) {
            if (value == null) {
                return null;
            }
            return new GenderRatio(
                    value.getMaleUsers(),
                    value.getFemaleUsers(),
                    value.getUnknownUsers(),
                    value.getMalePercentage(),
                    value.getFemalePercentage()
            );
        }
    }

    public record DepartmentRanking(
            int rank,
            String deptName,
            long requestCount
    ) {
        public static DepartmentRanking from(univ.airconnect.statistics.dto.response.MainStatisticsResponse.DepartmentRanking value) {
            return new DepartmentRanking(
                    value.getRank(),
                    value.getDeptName(),
                    value.getRequestCount()
            );
        }
    }

    public record NoticeBroadcastResult(
            Long noticeId,
            int recipients,
            String title
    ) {
    }

    public record NoticeSummary(
            Long noticeId,
            String title,
            String deeplink,
            boolean activeUsersOnly,
            int recipientCount,
            Long createdByUserId,
            LocalDateTime createdAt
    ) {
    }

    public record NoticeDetail(
            Long noticeId,
            String title,
            String body,
            String deeplink,
            boolean activeUsersOnly,
            int recipientCount,
            Long createdByUserId,
            LocalDateTime createdAt
    ) {
    }
}
