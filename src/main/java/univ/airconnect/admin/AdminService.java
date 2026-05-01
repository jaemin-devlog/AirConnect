package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_ADMIN_SENDER_NAME = "운영팀";
    private static final DateTimeFormatter ADMIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AdminNoticeRepository adminNoticeRepository;
    private final UserRepository userRepository;
    private final UserReportRepository userReportRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final IapOrderRepository iapOrderRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final StatisticsService statisticsService;
    private final ObjectMapper objectMapper;

    public AdminDtos.PageResponse<AdminDtos.UserSummary> getUsers(Integer page,
                                                                  Integer size,
                                                                  UserStatus status,
                                                                  String keyword) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<User> result = userRepository.searchForAdmin(status, normalizeKeyword(keyword), pageable);
        Page<AdminDtos.UserSummary> mapped = result.map(this::toUserSummary);
        return AdminDtos.PageResponse.from(mapped);
    }

    public AdminDtos.PageResponse<AdminDtos.NoticeSummary> getNotices(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<AdminDtos.NoticeSummary> mapped = adminNoticeRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                .map(notice -> new AdminDtos.NoticeSummary(
                        notice.getId(),
                        notice.getTitle(),
                        notice.getDeeplink(),
                        notice.isActiveUsersOnly(),
                        notice.getRecipientCount(),
                        notice.getCreatedByUserId(),
                        notice.getCreatedAt()
                ));
        return AdminDtos.PageResponse.from(mapped);
    }

    public AdminDtos.NoticeDetail getNoticeDetail(Long noticeId) {
        AdminNotice notice = adminNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "공지를 찾을 수 없습니다."));
        return new AdminDtos.NoticeDetail(
                notice.getId(),
                notice.getTitle(),
                notice.getBody(),
                notice.getDeeplink(),
                notice.isActiveUsersOnly(),
                notice.getRecipientCount(),
                notice.getCreatedByUserId(),
                notice.getCreatedAt()
        );
    }

    public AdminDtos.UserDetail getUserDetail(Long userId) {
        User user = getRequiredUser(userId);
        return toUserDetail(user);
    }

    @Transactional
    public AdminDtos.UserDetail applyUserAction(Long adminUserId, Long userId, AdminRequests.UserActionRequest request) {
        User user = getRequiredUser(userId);
        String reason = trimToNull(request.reason());
        switch (request.action()) {
            case SUSPEND -> {
                user.suspend(request.until(), reason);
                sendAdminAnnouncementToUser(
                        user.getId(),
                        adminUserId,
                        buildUserActionMessage(
                                "회원님의 계정이 정지되었습니다.",
                                reason,
                                request.until(),
                                "정지 해제 예정일"
                        ),
                        Map.of(
                                "kind", "ADMIN_USER_ACTION",
                                "action", request.action().name(),
                                "reason", nullablePayloadValue(reason),
                                "until", nullablePayloadValue(formatDateTime(request.until()))
                        )
                );
            }
            case DELETE -> userService.deleteAccount(userId, null, null);
            case REACTIVATE -> user.reactivate();
            case RESTRICT_MATCHING -> {
                user.restrictMatching(request.until(), reason);
                sendAdminAnnouncementToUser(
                        user.getId(),
                        adminUserId,
                        buildUserActionMessage(
                                "회원님의 매칭 기능이 제한되었습니다.",
                                reason,
                                request.until(),
                                "제한 해제 예정일"
                        ),
                        Map.of(
                                "kind", "ADMIN_USER_ACTION",
                                "action", request.action().name(),
                                "reason", nullablePayloadValue(reason),
                                "until", nullablePayloadValue(formatDateTime(request.until()))
                        )
                );
            }
            case CLEAR_MATCHING_RESTRICTION -> user.clearMatchingRestriction();
        }
        return toUserDetail(getRequiredUser(userId));
    }

    public AdminDtos.PageResponse<AdminDtos.MatchingRecord> getMatchings(Integer page,
                                                                         Integer size,
                                                                         ConnectionStatus status,
                                                                         Long userId) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<MatchingConnection> result = matchingConnectionRepository.searchForAdmin(status, userId, pageable);

        Set<Long> userIds = new LinkedHashSet<>();
        for (MatchingConnection connection : result.getContent()) {
            userIds.add(connection.getUser1Id());
            userIds.add(connection.getUser2Id());
        }
        Map<Long, User> users = loadUsers(userIds);

        Page<AdminDtos.MatchingRecord> mapped = result.map(connection -> new AdminDtos.MatchingRecord(
                connection.getId(),
                connection.getStatus(),
                connection.getRequesterId(),
                connection.getUser1Id(),
                getNickname(users.get(connection.getUser1Id())),
                connection.getUser2Id(),
                getNickname(users.get(connection.getUser2Id())),
                connection.getChatRoomId(),
                connection.getConnectedAt(),
                connection.getRespondedAt()
        ));
        return AdminDtos.PageResponse.from(mapped);
    }

    public AdminDtos.PageResponse<AdminDtos.ReportRecord> getReports(Integer page,
                                                                     Integer size,
                                                                     ReportStatus status,
                                                                     Long reportedUserId) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<UserReport> result = userReportRepository.searchForAdmin(status, reportedUserId, pageable);

        Set<Long> userIds = new LinkedHashSet<>();
        for (UserReport report : result.getContent()) {
            userIds.add(report.getReporterUserId());
            userIds.add(report.getReportedUserId());
        }
        Map<Long, User> users = loadUsers(userIds);

        Page<AdminDtos.ReportRecord> mapped = result.map(report -> new AdminDtos.ReportRecord(
                report.getId(),
                report.getReporterUserId(),
                getNickname(users.get(report.getReporterUserId())),
                report.getReportedUserId(),
                getNickname(users.get(report.getReportedUserId())),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        ));
        return AdminDtos.PageResponse.from(mapped);
    }

    @Transactional
    public AdminDtos.ReportRecord updateReportStatus(Long adminUserId, Long reportId, AdminRequests.ReportStatusUpdateRequest request) {
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
        validateReportStatusUpdateRequest(request);
        report.updateStatus(request.status());
        notifyReporterForReportStatus(adminUserId, report, request);

        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(report.getReporterUserId());
        userIds.add(report.getReportedUserId());
        Map<Long, User> users = loadUsers(userIds);
        return new AdminDtos.ReportRecord(
                report.getId(),
                report.getReporterUserId(),
                getNickname(users.get(report.getReporterUserId())),
                report.getReportedUserId(),
                getNickname(users.get(report.getReportedUserId())),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    public AdminDtos.TicketBalance getTicketBalance(Long userId) {
        User user = getRequiredUser(userId);
        return new AdminDtos.TicketBalance(user.getId(), user.getTickets());
    }

    public AdminDtos.PageResponse<AdminDtos.TicketLedgerItem> getTicketLedger(Long userId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<AdminDtos.TicketLedgerItem> mapped = ticketLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(ledger -> new AdminDtos.TicketLedgerItem(
                        ledger.getId(),
                        ledger.getChangeAmount(),
                        ledger.getBeforeAmount(),
                        ledger.getAfterAmount(),
                        ledger.getReason(),
                        ledger.getRefType().name(),
                        ledger.getRefId(),
                        ledger.getCreatedAt()
                ));
        return AdminDtos.PageResponse.from(mapped);
    }

    @Transactional
    public AdminDtos.TicketBalance adjustTickets(Long adminUserId, AdminRequests.TicketAdjustmentRequest request) {
        if (request.amount() == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "티켓 변경량은 0일 수 없습니다.");
        }

        User user = userRepository.findByIdForUpdate(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        int before = user.getTickets();
        try {
            user.adjustTickets(request.amount());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e.getMessage());
        }
        int after = user.getTickets();

        ticketLedgerRepository.save(
                TicketLedger.adjustByAdmin(
                        user.getId(),
                        request.amount(),
                        before,
                        after,
                        "ADMIN:" + request.reason().trim(),
                        "admin-adjustment:" + UUID.randomUUID()
                )
        );

        sendAdminAnnouncementToUser(
                user.getId(),
                adminUserId,
                buildTicketAdjustmentMessage(request.amount(), request.reason()),
                Map.of(
                        "kind", "ADMIN_TICKET_ADJUSTMENT",
                        "amount", request.amount(),
                        "reason", request.reason().trim(),
                        "beforeTickets", before,
                        "afterTickets", after
                )
        );

        return new AdminDtos.TicketBalance(user.getId(), user.getTickets());
    }

    public AdminDtos.StatisticsOverview getStatisticsOverview() {
        MainStatisticsResponse main = statisticsService.getMainStatistics();
        return new AdminDtos.StatisticsOverview(
                main.getTotalRegisteredUsers(),
                main.getDailyActiveUsers(),
                AdminDtos.GenderRatio.from(main.getGenderRatio()),
                main.getTotalMatchSuccessCount(),
                mapDepartmentRankings(main.getTopRequestedDepartments()),
                ticketLedgerRepository.sumGrantedTickets(),
                ticketLedgerRepository.sumConsumedTickets(),
                userReportRepository.countByStatus(ReportStatus.OPEN),
                LocalDateTime.now()
        );
    }

    @Transactional
    public AdminDtos.NoticeBroadcastResult broadcastNotice(Long adminUserId, AdminRequests.NoticeBroadcastRequest request) {
        boolean activeUsersOnly = !Boolean.FALSE.equals(request.activeUsersOnly());
        List<Long> recipientIds = !activeUsersOnly
                ? userRepository.findIdsByStatusNot(UserStatus.DELETED)
                : userRepository.findIdsByStatus(UserStatus.ACTIVE);

        String payloadJson = toPayloadJson(Map.of(
                "kind", "ADMIN_NOTICE",
                "title", request.title(),
                "body", request.body()
        ));

        for (Long userId : recipientIds) {
            notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                    userId,
                    NotificationType.SYSTEM_ANNOUNCEMENT,
                    request.title(),
                    request.body(),
                    request.deeplink(),
                    null,
                    null,
                    payloadJson,
                    null
            ));
        }

        AdminNotice savedNotice = adminNoticeRepository.save(
                AdminNotice.create(
                        adminUserId,
                        request.title(),
                        request.body(),
                        request.deeplink(),
                        activeUsersOnly,
                        recipientIds.size()
                )
        );

        return new AdminDtos.NoticeBroadcastResult(savedNotice.getId(), recipientIds.size(), request.title());
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Map<Long, User> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> mapped = new HashMap<>();
        for (User user : userRepository.findAllByIdWithProfile(userIds)) {
            mapped.put(user.getId(), user);
        }
        return mapped;
    }

    private AdminDtos.UserSummary toUserSummary(User user) {
        return new AdminDtos.UserSummary(
                user.getId(),
                user.getProvider().name(),
                user.getSocialId(),
                user.getPrimaryEmail(),
                deriveSchoolName(user.getPrimaryEmail()),
                user.getDeptName(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getOnboardingStatus(),
                user.getUserProfile() != null ? user.getUserProfile().getGender() : null,
                user.getTickets(),
                user.getCreatedAt(),
                user.getLastActiveAt(),
                user.isMatchingRestricted()
        );
    }

    private AdminDtos.UserDetail toUserDetail(User user) {
        return new AdminDtos.UserDetail(
                user.getId(),
                user.getProvider().name(),
                user.getSocialId(),
                user.getPrimaryEmail(),
                deriveSchoolName(user.getPrimaryEmail()),
                user.getDeptName(),
                user.getNickname(),
                user.getName(),
                user.getStudentNum(),
                user.getRole(),
                user.getStatus(),
                user.getOnboardingStatus(),
                user.getUserProfile() != null ? user.getUserProfile().getGender() : null,
                user.getTickets(),
                user.getCreatedAt(),
                user.getLastActiveAt(),
                user.getDeletedAt(),
                user.getSuspendedUntil(),
                user.getRestrictedAt(),
                user.getRestrictedUntil(),
                user.getRestrictedReason(),
                userReportRepository.countByReportedUserIdAndStatus(user.getId(), ReportStatus.OPEN),
                loadPurchaseHistories(user.getId()),
                loadSentRequestHistories(user.getId()),
                loadTicketUsageHistories(user.getId()),
                loadApiUsageHistories(user.getId())
        );
    }

    private String deriveSchoolName(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.toLowerCase(Locale.ROOT).endsWith("@office.hanseo.ac.kr") ? "HANSEO" : null;
    }

    private List<AdminDtos.DepartmentRanking> mapDepartmentRankings(List<MainStatisticsResponse.DepartmentRanking> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }
        return rankings.stream()
                .map(AdminDtos.DepartmentRanking::from)
                .toList();
    }

    private List<AdminDtos.PurchaseHistoryItem> loadPurchaseHistories(Long userId) {
        return iapOrderRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> new AdminDtos.PurchaseHistoryItem(
                        order.getId(),
                        order.getStore(),
                        order.getProductId(),
                        order.getStatus(),
                        order.getGrantedTickets(),
                        order.getBeforeTickets(),
                        order.getAfterTickets(),
                        order.getTransactionId(),
                        firstNonBlank(order.getOrderId(), order.getPurchaseToken()),
                        order.getProcessedAt(),
                        order.getCreatedAt()
                ))
                .toList();
    }

    private List<AdminDtos.SentRequestHistoryItem> loadSentRequestHistories(Long userId) {
        List<MatchingConnection> connections = matchingConnectionRepository.findTop20ByRequesterIdOrderByRecentDesc(userId);
        Set<Long> targetUserIds = new LinkedHashSet<>();
        for (MatchingConnection connection : connections) {
            targetUserIds.add(otherUserId(connection, userId));
        }
        Map<Long, User> users = loadUsers(targetUserIds);

        return connections.stream()
                .map(connection -> {
                    Long targetUserId = otherUserId(connection, userId);
                    return new AdminDtos.SentRequestHistoryItem(
                            connection.getId(),
                            connection.getStatus(),
                            targetUserId,
                            getNickname(users.get(targetUserId)),
                            connection.getChatRoomId(),
                            connection.getConnectedAt(),
                            connection.getRespondedAt()
                    );
                })
                .toList();
    }

    private List<AdminDtos.TicketUsageHistoryItem> loadTicketUsageHistories(Long userId) {
        return ticketLedgerRepository.findTop20UsageHistoryByUserId(userId).stream()
                .map(ledger -> new AdminDtos.TicketUsageHistoryItem(
                        ledger.getId(),
                        Math.abs(ledger.getChangeAmount()),
                        ledger.getBeforeAmount(),
                        ledger.getAfterAmount(),
                        ledger.getReason(),
                        ledger.getRefType().name(),
                        ledger.getRefId(),
                        ledger.getCreatedAt()
                ))
                .toList();
    }

    private List<AdminDtos.ApiUsageHistoryItem> loadApiUsageHistories(Long userId) {
        return analyticsEventRepository.findTop20ByUserIdOrderByOccurredAtDescIdDesc(userId).stream()
                .map(event -> new AdminDtos.ApiUsageHistoryItem(
                        event.getId(),
                        event.getType(),
                        event.getSource(),
                        event.getScreenName(),
                        event.getSessionId(),
                        event.getDeviceId(),
                        event.getPayloadJson(),
                        event.getOccurredAt()
                ))
                .toList();
    }

    private Long otherUserId(MatchingConnection connection, Long userId) {
        return Objects.equals(connection.getUser1Id(), userId) ? connection.getUser2Id() : connection.getUser1Id();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String getNickname(User user) {
        if (user == null) {
            return null;
        }
        return user.getNickname() != null ? user.getNickname() : user.getName();
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = trimToNull(keyword);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateReportStatusUpdateRequest(AdminRequests.ReportStatusUpdateRequest request) {
        String reason = trimToNull(request.reason());
        if ((request.status() == ReportStatus.RESOLVED || request.status() == ReportStatus.REJECTED) && reason == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "해당 신고 상태에는 처리 사유가 필요합니다.");
        }
    }

    private void notifyReporterForReportStatus(
            Long adminUserId,
            UserReport report,
            AdminRequests.ReportStatusUpdateRequest request
    ) {
        String reason = trimToNull(request.reason());
        String message = switch (request.status()) {
            case IN_REVIEW -> "신고가 검토중입니다. 빠른 시일 내에 처리됩니다.";
            case RESOLVED -> "신고가 처리되었습니다. " + reason;
            case REJECTED -> "신고가 기각되었습니다. 사유: " + reason;
            default -> null;
        };

        if (message == null) {
            return;
        }

        sendAdminAnnouncementToUser(
                report.getReporterUserId(),
                adminUserId,
                message,
                Map.of(
                        "kind", "ADMIN_REPORT_STATUS_UPDATE",
                        "reportId", report.getId(),
                        "status", request.status().name(),
                        "reason", nullablePayloadValue(reason),
                        "reportedUserId", report.getReportedUserId()
                )
        );
    }

    private void sendAdminAnnouncementToUser(
            Long targetUserId,
            Long adminUserId,
            String body,
            Map<String, Object> payload
    ) {
        notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                targetUserId,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                resolveAdminSenderName(adminUserId),
                body,
                null,
                adminUserId,
                null,
                toPayloadJson(enrichAdminPayload(adminUserId, payload)),
                null
        ));
    }

    private Map<String, Object> enrichAdminPayload(Long adminUserId, Map<String, Object> payload) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        enriched.put("senderName", resolveAdminSenderName(adminUserId));
        enriched.put("senderUserId", adminUserId);
        enriched.putAll(payload);
        return enriched;
    }

    private String resolveAdminSenderName(Long adminUserId) {
        if (adminUserId == null) {
            return DEFAULT_ADMIN_SENDER_NAME;
        }
        User adminUser = getRequiredUser(adminUserId);
        if (trimToNull(adminUser.getName()) != null) {
            return adminUser.getName().trim();
        }
        if (trimToNull(adminUser.getNickname()) != null) {
            return adminUser.getNickname().trim();
        }
        return DEFAULT_ADMIN_SENDER_NAME;
    }

    private String buildUserActionMessage(
            String actionLead,
            String reason,
            LocalDateTime until,
            String untilLabel
    ) {
        StringBuilder builder = new StringBuilder(actionLead);
        if (reason != null) {
            builder.append(" 사유: ").append(reason).append(".");
        }
        String formattedUntil = formatDateTime(until);
        if (formattedUntil != null) {
            builder.append(" ").append(untilLabel).append(": ").append(formattedUntil).append(".");
        }
        return builder.toString().trim();
    }

    private String buildTicketAdjustmentMessage(int amount, String reason) {
        String trimmedReason = requestSafeReason(reason);
        if (amount > 0) {
            return "티켓 " + amount + "개가 지급되었습니다. 사유: " + trimmedReason + ".";
        }
        return "티켓 " + Math.abs(amount) + "개가 차감되었습니다. 사유: " + trimmedReason + ".";
    }

    private String requestSafeReason(String reason) {
        String trimmed = trimToNull(reason);
        return trimmed != null ? trimmed : "운영 정책에 따른 조치";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(ADMIN_TIME_FORMATTER);
    }

    private Object nullablePayloadValue(Object value) {
        return value != null ? value : "";
    }

    private String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "공지 payload 생성에 실패했습니다.");
        }
    }
}
