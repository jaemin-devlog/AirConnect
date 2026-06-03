package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.repository.NotificationOutboxRepository;

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
    private final AnalyticsEventRepository analyticsEventRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final AdminAuditLogService adminAuditLogService;

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
        long chatRoomsCreated = matchingConnectionRepository
                .countByStatusAndChatRoomIdIsNotNullAndRespondedAtGreaterThanEqual(ConnectionStatus.ACCEPTED, since);

        List<AdminDtos.FunnelStep> steps = List.of(
                new AdminDtos.FunnelStep("recommendation_refreshed", "추천 새로고침", recommendationRefreshes, null),
                new AdminDtos.FunnelStep("request_sent", "매칭 요청", requestsSent, percentage(requestsSent, recommendationRefreshes)),
                new AdminDtos.FunnelStep("request_accepted", "요청 수락", requestsAccepted, percentage(requestsAccepted, requestsSent)),
                new AdminDtos.FunnelStep("chat_room_created", "채팅방 생성", chatRoomsCreated, percentage(chatRoomsCreated, requestsAccepted))
        );

        AdminDtos.MatchingFunnel response = new AdminDtos.MatchingFunnel(
                days,
                since,
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
                        "chatRoomsCreated", chatRoomsCreated
                )
        );
        return response;
    }

    public AdminDtos.IntegrityReport getIntegrityReport(Long adminUserId) {
        List<AdminDtos.IntegrityCheckItem> checks = List.of(
                check(
                        "chat_rooms_without_members",
                        "고아 채팅방",
                        chatRoomRepository.countRoomsWithoutVisibleMembers(),
                        "참여 중인 멤버가 없는 채팅방입니다."
                ),
                check(
                        "personal_rooms_invalid_member_count",
                        "개인 채팅방 멤버 수 불일치",
                        chatRoomMemberRepository.countPersonalRoomsWithInvalidVisibleMemberCount(),
                        "개인 채팅방인데 보이는 멤버 수가 2명이 아닌 방입니다."
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
                        "티켓 원장 금액 불일치",
                        ticketLedgerRepository.countBrokenAmountRows(),
                        "afterAmount가 beforeAmount + changeAmount와 다른 원장입니다."
                ),
                check(
                        "ticket_ledger_duplicate_refs",
                        "티켓 원장 참조 중복",
                        ticketLedgerRepository.countDuplicateRefRows(),
                        "refType/refId 조합이 중복된 원장입니다."
                )
        );

        long failureCount = checks.stream().filter(item -> "FAIL".equals(item.status())).count();
        AdminDtos.IntegrityReport response = new AdminDtos.IntegrityReport(
                checks,
                0,
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

    private int safeDays(Integer requestedDays) {
        if (requestedDays == null || requestedDays < 1) {
            return DEFAULT_FUNNEL_DAYS;
        }
        return Math.min(requestedDays, MAX_FUNNEL_DAYS);
    }
}
