package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import univ.airconnect.analytics.domain.AnalyticsEventSource;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
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
            String socialEmail,
            String verifiedSchoolEmail,
            String schoolName,
            String deptName,
            String nickname,
            String name,
            Integer studentNum,
            LocalDateTime lastNicknameChangedAt,
            UserRole role,
            UserStatus status,
            OnboardingStatus onboardingStatus,
            Gender gender,
            UserProfileDetail profile,
            Integer tickets,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            LocalDateTime deletedAt,
            LocalDateTime suspendedUntil,
            LocalDateTime restrictedAt,
            LocalDateTime restrictedUntil,
            String restrictedReason,
            boolean matchingRestricted,
            long openReportCount,
            List<PurchaseHistoryItem> purchaseHistories,
            List<SentRequestHistoryItem> sentRequestHistories,
            List<TicketUsageHistoryItem> ticketUsageHistories,
            List<ApiUsageHistoryItem> apiUsageHistories
    ) {
    }

    public record UserProfileDetail(
            Integer height,
            Integer age,
            String mbti,
            String smoking,
            Gender gender,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram,
            String profileImagePath,
            LocalDateTime updatedAt
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

    public record ChatRoomSummary(
            Long chatRoomId,
            String name,
            univ.airconnect.chat.domain.ChatRoomType type,
            Long connectionId,
            Long user1Id,
            String user1Nickname,
            Long user2Id,
            String user2Nickname,
            LocalDateTime lastMessageAt,
            long memberCount,
            long messageCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ChatRoomDetail(
            ChatRoomSummary room,
            List<ChatRoomMemberItem> members
    ) {
    }

    public record ChatRoomMemberItem(
            Long memberId,
            Long userId,
            String nickname,
            LocalDateTime joinedAt,
            boolean hidden
    ) {
    }

    public record ReportDetail(
            ReportRecord record,
            Long version,
            String internalMemo,
            String reporterReply,
            Long handledByUserId,
            ReportEvidence evidence,
            List<ReportAction> actions
    ) {
    }

    public record ReportEvidence(
            String status,
            univ.airconnect.moderation.domain.ReportSourceType sourceType,
            Long userId,
            Long roomId,
            Long connectionId,
            Long messageId
    ) {
    }

    public record ReportAction(Long id, Long actorUserId, Long userId, String action,
                               String reason, LocalDateTime until, LocalDateTime createdAt) {
    }

    public record TicketAdjustmentResult(
            String operationId,
            String status,
            Long userId,
            int amount,
            Integer beforeTickets,
            Integer afterTickets,
            Long ledgerId,
            LocalDateTime completedAt,
            String rejectionCode,
            String rejectionMessage
    ) {
    }

    public record ChatHistory(Long roomId, List<ChatMessageItem> items, Long nextBeforeId,
                              boolean hasMore, LocalDateTime inspectedAt) {
    }

    public record ChatMessageInspection(
            Long roomId,
            AdminRequests.ChatInspectionReason reason,
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime inspectedAt,
            PageResponse<ChatMessageItem> messages
    ) {
    }

    public record ChatMessageItem(
            Long messageId,
            Long roomId,
            Long senderId,
            String senderNickname,
            String content,
            univ.airconnect.chat.domain.MessageType type,
            boolean deleted,
            LocalDateTime deletedAt,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {
    }

    public record OutboxMonitor(
            List<OutboxStatusCount> statusCounts,
            long oldPendingCount,
            long staleProcessingCount,
            Double averageDeliverySeconds,
            List<OutboxFailureItem> recentFailures,
            LocalDateTime generatedAt
    ) {
    }

    public record OutboxStatusCount(
            NotificationDeliveryStatus status,
            long count
    ) {
    }

    public record OutboxFailureItem(
            Long outboxId,
            Long notificationId,
            Long userId,
            NotificationDeliveryStatus status,
            Integer attemptCount,
            String lastErrorCode,
            String lastErrorMessage,
            LocalDateTime updatedAt
    ) {
    }

    public record OperationsSummary(
            LocalDateTime operationStartedAt,
            long operationDays,
            long totalRegisteredUsers,
            long onboardingCompletedUsers,
            long dailyActiveUsers,
            long weeklyActiveUsers,
            long monthlyActiveUsers,
            long totalMatchSuccessCount,
            long totalChatMessages,
            long unresolvedReports,
            long outboxBacklog,
            LocalDateTime generatedAt
    ) {
    }

    public record ApiUsageStatistics(
            int days,
            LocalDateTime since,
            long totalApiCallCount,
            long uniqueEndpointCount,
            ApiEndpointUsage mostCalledApi,
            List<ApiEndpointUsage> topApiCalls,
            LocalDateTime generatedAt
    ) {
    }

    public record ApiEndpointUsage(
            String method,
            String path,
            long count,
            Double averageDurationMs,
            LocalDateTime lastCalledAt
    ) {
    }

    public record MatchingFunnel(
            int days,
            LocalDateTime since,
            long recommendationViewCount,
            long requestCount,
            long acceptedCount,
            long rejectedOrExpiredCount,
            Integer acceptanceRatePercentage,
            Double averageResponseSeconds,
            long chatRoomCreatedCount,
            List<FunnelStep> steps,
            LocalDateTime generatedAt
    ) {
    }

    public record FunnelStep(
            String key,
            String label,
            long count,
            Integer conversionFromPreviousPercentage
    ) {
    }

    public record GroupMatchingFunnel(
            int days,
            LocalDateTime since,
            long teamRoomCreatedCount,
            long teamRoomJoinCount,
            long readyTeamCount,
            long queueEnteredCount,
            long matchSuccessCount,
            long finalGroupChatRoomCreatedCount,
            Double averageQueueWaitSeconds,
            List<FunnelStep> steps,
            LocalDateTime generatedAt
    ) {
    }

    public record NotificationOperations(
            int days,
            LocalDateTime since,
            long notificationCreatedCount,
            List<OutboxStatusCount> outboxStatusCounts,
            Integer deliverySuccessRatePercentage,
            List<FailureReasonCount> failureReasons,
            long invalidTokenCount,
            Double averageProcessingSeconds,
            LocalDateTime generatedAt
    ) {
    }

    public record FailureReasonCount(
            String reason,
            long count
    ) {
    }

    public record OperationsManagement(
            long reportReceivedCount,
            long reportProcessedCount,
            long reportUnresolvedCount,
            Double averageReportProcessingSeconds,
            long suspendedActionCount,
            long matchingRestrictedActionCount,
            long matchingRestrictionClearedActionCount,
            long noticeBroadcastCount,
            long noticeRecipientCount,
            long auditLogCount,
            LocalDateTime generatedAt
    ) {
    }

    public record IntegrityReport(
            List<IntegrityCheckItem> checks,
            long warningCount,
            long failureCount,
            LocalDateTime generatedAt
    ) {
    }

    public record IntegrityCheckItem(
            String key,
            String label,
            String status,
            long count,
            String description
    ) {
    }

    public record IntegrityIssueItem(
            String key,
            String itemId,
            String title,
            String status,
            String detail,
            Long userId,
            Long relatedUserId,
            Long roomId,
            Long matchingId,
            Long outboxId,
            Long notificationId,
            Long ticketHistoryId,
            String referenceValue,
            LocalDateTime occurredAt
    ) {
    }

    public record OutboxRetryResult(
            Long outboxId,
            NotificationDeliveryStatus status,
            boolean queued,
            String result,
            LocalDateTime requestedAt
    ) {
    }

    public record AuditLogItem(
            Long auditLogId,
            Long actorUserId,
            AdminAuditAction action,
            String targetType,
            String targetId,
            String summary,
            String reason,
            String metadataJson,
            LocalDateTime createdAt
    ) {
    }

    public record AuditLogStatistics(
            int days,
            LocalDateTime since,
            long totalAuditLogCount,
            long apiCallCount,
            long uniqueActorCount,
            List<ApiCallCount> topApiCalls,
            List<AuditActionCount> topActions,
            LocalDateTime generatedAt
    ) {
    }

    public record ApiCallCount(
            String method,
            String path,
            long count,
            Double averageDurationMs
    ) {
    }

    public record AuditActionCount(
            AdminAuditAction action,
            long count
    ) {
    }

    public record UserPermanentDeleteResult(
            Long userId,
            String provider,
            String socialId,
            String status,
            long deletedProfileRows,
            long deletedSchoolConsentRows,
            long deletedChatRoomMemberRows,
            long deletedRefreshTokenRows,
            long deletedSocialDeviceBindingRows,
            long deletedPushDeviceRows,
            long deletedNotificationPreferenceRows,
            long deletedNotificationRows,
            long deletedNotificationOutboxRows,
            long deletedPushEventRows,
            long deletedUserMilestoneRows,
            boolean userDeleted
    ) {
    }
}
