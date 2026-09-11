package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.repository.ApiRequestLogRepository;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationsService {

    private static final int DEFAULT_FUNNEL_DAYS = 30;
    private static final int MAX_FUNNEL_DAYS = 365;

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final ApiRequestLogRepository apiRequestLogRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserReportRepository userReportRepository;
    private final GTemporaryTeamRoomRepository gTemporaryTeamRoomRepository;
    private final GTemporaryTeamMemberRepository gTemporaryTeamMemberRepository;
    private final GTeamReadyStateRepository gTeamReadyStateRepository;
    private final GMatchResultRepository gMatchResultRepository;
    private final GFinalGroupChatRoomRepository gFinalGroupChatRoomRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final AdminNoticeRepository adminNoticeRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminIntegrityQueryRepository adminIntegrityQueryRepository;

    public AdminDtos.OperationsSummary getOperationsSummary(Long adminUserId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime operationStartedAt = userRepository.findFirstByOrderByCreatedAtAsc()
                .map(user -> user.getCreatedAt())
                .orElse(null);

        long pendingOutbox = notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PENDING);
        long processingOutbox = notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PROCESSING);
        long unresolvedReports = userReportRepository.countByStatusIn(List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW));

        AdminDtos.OperationsSummary response = new AdminDtos.OperationsSummary(
                operationStartedAt,
                operationStartedAt == null ? 0 : Math.max(1, Duration.between(operationStartedAt, now).toDays() + 1),
                userRepository.count(),
                userRepository.countByOnboardingStatus(OnboardingStatus.FULL),
                userRepository.countByLastActiveAtGreaterThanEqual(now.minusDays(1)),
                userRepository.countByLastActiveAtGreaterThanEqual(now.minusDays(7)),
                userRepository.countByLastActiveAtGreaterThanEqual(now.minusDays(30)),
                matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED),
                chatMessageRepository.countByDeletedFalse(),
                unresolvedReports,
                pendingOutbox + processingOutbox,
                now
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.OPERATIONS_SUMMARY_VIEWED,
                "OPERATIONS",
                "summary",
                "운영 요약 대시보드를 조회했습니다.",
                null,
                Map.of(
                        "totalRegisteredUsers", response.totalRegisteredUsers(),
                        "dailyActiveUsers", response.dailyActiveUsers(),
                        "outboxBacklog", response.outboxBacklog()
                )
        );
        return response;
    }

    public AdminDtos.ApiUsageStatistics getApiUsageStatistics(Long adminUserId, Integer requestedDays) {
        int days = safeDays(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<AdminDtos.ApiEndpointUsage> topApiCalls = apiRequestLogRepository.findTopEndpointsSince(since, org.springframework.data.domain.PageRequest.of(0, 10))
                .stream()
                .map(row -> new AdminDtos.ApiEndpointUsage(
                        row.getMethod(),
                        row.getPath(),
                        row.getCount(),
                        row.getAverageDurationMs(),
                        row.getLastCalledAt()
                ))
                .toList();

        AdminDtos.ApiUsageStatistics response = new AdminDtos.ApiUsageStatistics(
                days,
                since,
                apiRequestLogRepository.countByCreatedAtGreaterThanEqual(since),
                apiRequestLogRepository.countDistinctEndpointsSince(since),
                topApiCalls.isEmpty() ? null : topApiCalls.get(0),
                topApiCalls,
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.API_USAGE_VIEWED,
                "OPERATIONS",
                "api-usage",
                "전체 API 사용 통계를 조회했습니다.",
                null,
                Map.of(
                        "days", days,
                        "totalApiCallCount", response.totalApiCallCount(),
                        "mostCalledApi", response.mostCalledApi() == null
                                ? ""
                                : response.mostCalledApi().method() + " " + response.mostCalledApi().path()
                )
        );
        return response;
    }

    public AdminDtos.OutboxMonitor getOutboxMonitor(Long adminUserId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stalePendingThreshold = now.minusMinutes(5);
        LocalDateTime staleProcessingThreshold = now.minusMinutes(15);
        LocalDateTime latencyWindowStart = now.minusDays(7);

        List<AdminDtos.OutboxStatusCount> statusCounts = new ArrayList<>();
        for (NotificationDeliveryStatus status : NotificationDeliveryStatus.values()) {
            statusCounts.add(new AdminDtos.OutboxStatusCount(
                    status,
                    notificationOutboxRepository.countByStatus(status)
            ));
        }

        List<AdminDtos.OutboxFailureItem> recentFailures = notificationOutboxRepository
                .findTop10ByStatusOrderByUpdatedAtDesc(NotificationDeliveryStatus.FAILED)
                .stream()
                .map(this::toOutboxFailureItem)
                .toList();

        AdminDtos.OutboxMonitor response = new AdminDtos.OutboxMonitor(
                statusCounts,
                notificationOutboxRepository.countByStatusAndNextAttemptAtBefore(
                        NotificationDeliveryStatus.PENDING,
                        stalePendingThreshold
                ),
                notificationOutboxRepository.countProcessingOlderThan(
                        NotificationDeliveryStatus.PROCESSING,
                        staleProcessingThreshold
                ),
                notificationOutboxRepository.averageDeliverySecondsSince(latencyWindowStart),
                recentFailures,
                now
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.OUTBOX_MONITOR_VIEWED,
                "OPERATIONS",
                "outbox",
                "Outbox 모니터링 대시보드를 조회했습니다.",
                null,
                Map.of(
                        "oldPendingCount", response.oldPendingCount(),
                        "staleProcessingCount", response.staleProcessingCount()
                )
        );
        return response;
    }

    public AdminDtos.MatchingFunnel getMatchingFunnel(Long adminUserId, Integer requestedDays) {
        int days = safeDays(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        long recommendationRefreshes = analyticsEventRepository.countByTypeAndOccurredAtGreaterThanEqual(
                AnalyticsEventType.MATCH_RECOMMENDATION_REFRESHED,
                since
        );
        long requestsSent = analyticsEventRepository.countByTypeAndOccurredAtGreaterThanEqual(
                AnalyticsEventType.MATCH_REQUEST_SENT,
                since
        );
        long requestsAccepted = analyticsEventRepository.countByTypeAndOccurredAtGreaterThanEqual(
                AnalyticsEventType.MATCH_REQUEST_ACCEPTED,
                since
        );
        long rejectedOrExpired = matchingConnectionRepository.countByStatusInAndRespondedAtGreaterThanEqual(
                List.of(ConnectionStatus.REJECTED, ConnectionStatus.CANCELLED, ConnectionStatus.EXPIRED),
                since
        );
        long chatRoomsCreated = matchingConnectionRepository
                .countByStatusAndChatRoomIdIsNotNullAndRespondedAtGreaterThanEqual(ConnectionStatus.ACCEPTED, since);
        Double averageResponseSeconds = matchingConnectionRepository.averageResponseSecondsSince(since);

        List<AdminDtos.FunnelStep> steps = List.of(
                new AdminDtos.FunnelStep("recommendation_refreshed", "추천 새로고침", recommendationRefreshes, null),
                new AdminDtos.FunnelStep("request_sent", "매칭 요청", requestsSent, percentage(requestsSent, recommendationRefreshes)),
                new AdminDtos.FunnelStep("request_accepted", "요청 수락", requestsAccepted, percentage(requestsAccepted, requestsSent)),
                new AdminDtos.FunnelStep("request_rejected_or_expired", "거절/취소/만료", rejectedOrExpired, percentage(rejectedOrExpired, requestsSent)),
                new AdminDtos.FunnelStep("chat_room_created", "채팅방 생성", chatRoomsCreated, percentage(chatRoomsCreated, requestsAccepted))
        );

        AdminDtos.MatchingFunnel response = new AdminDtos.MatchingFunnel(
                days,
                since,
                recommendationRefreshes,
                requestsSent,
                requestsAccepted,
                rejectedOrExpired,
                percentage(requestsAccepted, requestsSent),
                averageResponseSeconds,
                chatRoomsCreated,
                steps,
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.MATCHING_FUNNEL_VIEWED,
                "OPERATIONS",
                "matching-funnel",
                "매칭 퍼널을 조회했습니다.",
                null,
                Map.of(
                        "days", days,
                        "requestSent", requestsSent,
                        "requestAccepted", requestsAccepted,
                        "rejectedOrExpired", rejectedOrExpired,
                        "chatRoomsCreated", chatRoomsCreated
                )
        );
        return response;
    }

    public AdminDtos.GroupMatchingFunnel getGroupMatchingFunnel(Long adminUserId, Integer requestedDays) {
        int days = safeDays(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        long teamRoomCreated = gTemporaryTeamRoomRepository.countByCreatedAtGreaterThanEqual(since);
        long teamRoomJoined = gTemporaryTeamMemberRepository.countByJoinedAtGreaterThanEqual(since);
        long readyTeams = gTeamReadyStateRepository.countReadyTeamsSince(since);
        long queueEntered = gTemporaryTeamRoomRepository.countByQueuedAtGreaterThanEqual(since);
        long matchSuccess = gMatchResultRepository.countByMatchedAtGreaterThanEqual(since);
        long finalGroupChatCreated = gFinalGroupChatRoomRepository.countByCreatedAtGreaterThanEqual(since);
        Double averageQueueWaitSeconds = gTemporaryTeamRoomRepository.averageQueueWaitSecondsSince(since);

        List<AdminDtos.FunnelStep> steps = List.of(
                new AdminDtos.FunnelStep("team_room_created", "팀방 생성", teamRoomCreated, null),
                new AdminDtos.FunnelStep("team_room_joined", "팀방 참여", teamRoomJoined, percentage(teamRoomJoined, teamRoomCreated)),
                new AdminDtos.FunnelStep("ready_team", "준비 완료 팀", readyTeams, percentage(readyTeams, teamRoomCreated)),
                new AdminDtos.FunnelStep("queue_entered", "큐 진입", queueEntered, percentage(queueEntered, readyTeams)),
                new AdminDtos.FunnelStep("match_success", "매칭 성공", matchSuccess, percentage(matchSuccess, queueEntered)),
                new AdminDtos.FunnelStep("final_group_chat_created", "최종 그룹 채팅방 생성", finalGroupChatCreated, percentage(finalGroupChatCreated, matchSuccess))
        );

        AdminDtos.GroupMatchingFunnel response = new AdminDtos.GroupMatchingFunnel(
                days,
                since,
                teamRoomCreated,
                teamRoomJoined,
                readyTeams,
                queueEntered,
                matchSuccess,
                finalGroupChatCreated,
                averageQueueWaitSeconds,
                steps,
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.GROUP_MATCHING_FUNNEL_VIEWED,
                "OPERATIONS",
                "group-matching-funnel",
                "그룹 매칭 퍼널을 조회했습니다.",
                null,
                Map.of(
                        "days", days,
                        "teamRoomCreated", teamRoomCreated,
                        "queueEntered", queueEntered,
                        "matchSuccess", matchSuccess
                )
        );
        return response;
    }

    public AdminDtos.NotificationOperations getNotificationOperations(Long adminUserId, Integer requestedDays) {
        int days = safeDays(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<AdminDtos.OutboxStatusCount> statusCounts = outboxStatusCounts();
        long sent = countStatus(statusCounts, NotificationDeliveryStatus.SENT);
        long failed = countStatus(statusCounts, NotificationDeliveryStatus.FAILED);
        long skipped = countStatus(statusCounts, NotificationDeliveryStatus.SKIPPED);
        long completed = sent + failed + skipped;

        AdminDtos.NotificationOperations response = new AdminDtos.NotificationOperations(
                days,
                since,
                notificationRepository.countByCreatedAtGreaterThanEqual(since),
                statusCounts,
                percentage(sent, completed),
                notificationOutboxRepository.countFailuresByReason().stream()
                        .map(row -> new AdminDtos.FailureReasonCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                        .toList(),
                notificationOutboxRepository.countInvalidTokenFailures(),
                notificationOutboxRepository.averageProcessingSecondsSince(since),
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.NOTIFICATION_OPERATIONS_VIEWED,
                "OPERATIONS",
                "notifications",
                "알림/Outbox 운영 지표를 조회했습니다.",
                null,
                Map.of(
                        "days", days,
                        "notificationCreatedCount", response.notificationCreatedCount(),
                        "deliverySuccessRatePercentage", nullableValue(response.deliverySuccessRatePercentage())
                )
        );
        return response;
    }

    public AdminDtos.OperationsManagement getOperationsManagement(Long adminUserId) {
        long reportProcessed = userReportRepository.countByStatusIn(List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED));
        long reportUnresolved = userReportRepository.countByStatusIn(List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW));

        AdminDtos.OperationsManagement response = new AdminDtos.OperationsManagement(
                userReportRepository.count(),
                reportProcessed,
                reportUnresolved,
                userReportRepository.averageProcessingSeconds(),
                adminAuditLogRepository.countByActionAndMetadataJsonContaining(AdminAuditAction.USER_ACTION_APPLIED, "\"action\":\"SUSPEND\""),
                adminAuditLogRepository.countByActionAndMetadataJsonContaining(AdminAuditAction.USER_ACTION_APPLIED, "\"action\":\"RESTRICT_MATCHING\""),
                adminAuditLogRepository.countByActionAndMetadataJsonContaining(AdminAuditAction.USER_ACTION_APPLIED, "\"action\":\"CLEAR_MATCHING_RESTRICTION\""),
                adminNoticeRepository.count(),
                adminNoticeRepository.sumRecipientCount(),
                adminAuditLogRepository.count(),
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.OPERATIONS_MANAGEMENT_VIEWED,
                "OPERATIONS",
                "management",
                "운영 관리 지표를 조회했습니다.",
                null,
                Map.of(
                        "reportUnresolvedCount", response.reportUnresolvedCount(),
                        "auditLogCount", response.auditLogCount()
                )
        );
        return response;
    }

    public AdminDtos.IntegrityReport getIntegrityReport(Long adminUserId) {
        List<AdminDtos.IntegrityCheckItem> checks = List.of(
                observation(
                        "chat_rooms_without_members",
                        "표시 중인 참여자가 없는 방",
                        chatRoomRepository.countRoomsWithoutVisibleMembers(),
                        "종료된 임시방, 전체 퇴장, 방 숨김도 포함합니다. 이 수치만으로 장애나 멤버 누락을 판단할 수 없습니다."
                ),
                observation(
                        "personal_rooms_invalid_member_count",
                        "표시 중인 참여자가 2명이 아닌 1:1 방",
                        chatRoomMemberRepository.countPersonalRoomsWithInvalidVisibleMemberCount(),
                        "퇴장·차단에 따른 방 숨김도 포함합니다. 원래 참여자와 참여·퇴장 기록을 확인해야 하며 자동 복구 대상이 아닙니다."
                ),
                check(
                        "accepted_matching_without_chat_room",
                        "수락 매칭의 채팅방 누락",
                        matchingConnectionRepository.countByStatusAndChatRoomIdIsNull(ConnectionStatus.ACCEPTED),
                        "ACCEPTED 상태지만 chatRoomId가 없는 매칭입니다."
                ),
                check(
                        "accepted_matching_missing_chat_room_row",
                        "매칭-채팅방 참조 불일치",
                        matchingConnectionRepository.countAcceptedConnectionsWithMissingChatRoom(),
                        "매칭의 chatRoomId가 실제 채팅방 행을 가리키지 못하는 경우입니다."
                ),
                check(
                        "outbox_missing_notification",
                        "Outbox-Notification 불일치",
                        notificationOutboxRepository.countRowsMissingNotification(),
                        "Outbox가 원본 알림 행을 찾지 못하는 경우입니다."
                ),
                check(
                        "ticket_ledger_amount_mismatch",
                        "티켓 변동 내역 계산 불일치",
                        ticketLedgerRepository.countBrokenAmountRows(),
                        "변경 전 잔액에 증감 수량을 더한 값이 변경 후 잔액과 다른 내역입니다."
                ),
                check(
                        "ticket_ledger_duplicate_refs",
                        "티켓 변동 내역 연결 정보 중복",
                        ticketLedgerRepository.countDuplicateRefRows(),
                        "같은 참조 유형과 참조 번호로 기록된 티켓 변동 내역이 여러 건 있습니다."
                )
        );

        long failureCount = checks.stream().filter(item -> "FAIL".equals(item.status())).count();
        AdminDtos.IntegrityReport response = new AdminDtos.IntegrityReport(
                checks,
                checks.stream().filter(item -> "WARN".equals(item.status())).count(),
                failureCount,
                LocalDateTime.now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.INTEGRITY_CHECK_VIEWED,
                "OPERATIONS",
                "integrity",
                "데이터 정합성 점검 결과를 조회했습니다.",
                null,
                Map.of("failureCount", failureCount)
        );
        return response;
    }

    public AdminDtos.PageResponse<AdminDtos.IntegrityIssueItem> getIntegrityIssues(
            Long adminUserId, String key, Integer requestedPage, Integer requestedSize) {
        requireAdmin(adminUserId);
        int page = requestedPage == null ? 0 : Math.max(0, requestedPage);
        int size = requestedSize == null ? 20 : Math.max(1, Math.min(100, requestedSize));
        AdminDtos.PageResponse<AdminDtos.IntegrityIssueItem> response;
        try {
            response = adminIntegrityQueryRepository.find(key, page, size);
        } catch (IllegalArgumentException unsupported) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, unsupported.getMessage());
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.INTEGRITY_ISSUES_VIEWED,
                "INTEGRITY_CHECK",
                key,
                "데이터 점검 대상 목록을 조회했습니다.",
                null,
                Map.of("page", page, "size", size, "returnedCount", response.items().size())
        );
        return response;
    }

    @Transactional
    public AdminDtos.OutboxRetryResult retryFailedOutbox(Long adminUserId, Long outboxId) {
        requireAdmin(adminUserId);
        if (outboxId == null || outboxId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림 작업 번호를 확인해 주세요.");
        }
        NotificationOutbox outbox = notificationOutboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림 전송 작업을 찾을 수 없습니다."));

        if (outbox.getStatus() == NotificationDeliveryStatus.PENDING
                || outbox.getStatus() == NotificationDeliveryStatus.PROCESSING) {
            return new AdminDtos.OutboxRetryResult(
                    outboxId, outbox.getStatus(), false, "ALREADY_QUEUED", LocalDateTime.now());
        }
        if (outbox.getStatus() != NotificationDeliveryStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "최종 실패한 알림만 다시 시도할 수 있습니다.");
        }
        if (!notificationRepository.existsById(outbox.getNotificationId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "원본 알림이 없어 다시 보낼 수 없습니다.");
        }
        var device = pushDeviceRepository.findByIdAndUserId(outbox.getPushDeviceId(), outbox.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "대상 기기를 찾을 수 없어 다시 보낼 수 없습니다."));
        if (!Boolean.TRUE.equals(device.getActive())
                || !Boolean.TRUE.equals(device.getNotificationPermissionGranted())
                || device.getProvider() != outbox.getProvider()
                || !device.getPushToken().equals(outbox.getTargetToken())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "현재 활성 기기와 실패 당시 대상이 달라 다시 보낼 수 없습니다. 회원이 앱에서 알림 설정을 갱신해야 합니다.");
        }

        LocalDateTime requestedAt = LocalDateTime.now();
        outbox.queueManualRetry(requestedAt);
        adminAuditLogService.recordOutboxRetry(adminUserId,
                new AdminAuditLogService.NotificationOutboxRetryAudit(
                        outboxId,
                        outbox.getNotificationId(),
                        outbox.getUserId(),
                        outbox.getAttemptCount()
                ));
        return new AdminDtos.OutboxRetryResult(
                outboxId, NotificationDeliveryStatus.PENDING, true, "QUEUED", requestedAt);
    }

    private AdminDtos.OutboxFailureItem toOutboxFailureItem(NotificationOutbox outbox) {
        return new AdminDtos.OutboxFailureItem(
                outbox.getId(),
                outbox.getNotificationId(),
                outbox.getUserId(),
                outbox.getStatus(),
                outbox.getAttemptCount(),
                outbox.getLastErrorCode(),
                outbox.getLastErrorMessage(),
                outbox.getUpdatedAt()
        );
    }

    private List<AdminDtos.OutboxStatusCount> outboxStatusCounts() {
        List<AdminDtos.OutboxStatusCount> statusCounts = new ArrayList<>();
        for (NotificationDeliveryStatus status : NotificationDeliveryStatus.values()) {
            statusCounts.add(new AdminDtos.OutboxStatusCount(
                    status,
                    notificationOutboxRepository.countByStatus(status)
            ));
        }
        return statusCounts;
    }

    private long countStatus(List<AdminDtos.OutboxStatusCount> statusCounts, NotificationDeliveryStatus status) {
        return statusCounts.stream()
                .filter(item -> item.status() == status)
                .mapToLong(AdminDtos.OutboxStatusCount::count)
                .findFirst()
                .orElse(0L);
    }

    private AdminDtos.IntegrityCheckItem observation(String key, String label, long count, String description) {
        return new AdminDtos.IntegrityCheckItem(key, label, count > 0 ? "WARN" : "PASS", count, description);
    }

    private AdminDtos.IntegrityCheckItem check(String key, String label, long count, String description) {
        return new AdminDtos.IntegrityCheckItem(
                key,
                label,
                count == 0 ? "PASS" : "FAIL",
                count,
                description
        );
    }

    private Integer percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (int) Math.round((numerator * 100.0) / denominator);
    }

    private Object nullableValue(Object value) {
        return value != null ? value : "";
    }

    private int safeDays(Integer requestedDays) {
        if (requestedDays == null || requestedDays < 1) {
            return DEFAULT_FUNNEL_DAYS;
        }
        return Math.min(requestedDays, MAX_FUNNEL_DAYS);
    }

    private void requireAdmin(Long adminUserId) {
        var admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (admin.getRole() != UserRole.ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
