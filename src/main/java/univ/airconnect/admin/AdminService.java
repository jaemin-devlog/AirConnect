package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.Duration;
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final StatisticsService statisticsService;
    private final ObjectMapper objectMapper;
    private final AdminAuditLogService adminAuditLogService;

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

    public AdminDtos.PageResponse<AdminDtos.ChatRoomSummary> getChatRooms(Integer page,
                                                                          Integer size,
                                                                          ChatRoomType type,
                                                                          Long userId,
                                                                          String keyword) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size));
        Page<ChatRoom> result = chatRoomRepository.searchForAdmin(type, userId, normalizeKeyword(keyword), pageable);

        Set<Long> userIds = new LinkedHashSet<>();
        for (ChatRoom room : result.getContent()) {
            if (room.getUser1Id() != null) {
                userIds.add(room.getUser1Id());
            }
            if (room.getUser2Id() != null) {
                userIds.add(room.getUser2Id());
            }
        }
        Map<Long, User> users = loadUsers(userIds);

        Page<AdminDtos.ChatRoomSummary> mapped = result.map(room -> toChatRoomSummary(room, users));
        return AdminDtos.PageResponse.from(mapped);
    }

    public AdminDtos.ChatRoomDetail getChatRoomDetail(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다."));

        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomIdInWithUser(List.of(roomId));
        Set<Long> userIds = new LinkedHashSet<>();
        if (room.getUser1Id() != null) {
            userIds.add(room.getUser1Id());
        }
        if (room.getUser2Id() != null) {
            userIds.add(room.getUser2Id());
        }
        for (ChatRoomMember member : members) {
            userIds.add(member.getUser().getId());
        }
        Map<Long, User> users = loadUsers(userIds);

        return new AdminDtos.ChatRoomDetail(
                toChatRoomSummary(room, users),
                members.stream()
                        .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt))
                        .map(this::toChatRoomMemberItem)
                        .toList()
        );
    }

    /** Read-only cursor history. Authorization is rechecked on every page, including deleted text. */
    public AdminDtos.ChatHistory readChatHistory(Long adminUserId, Long roomId,
                                                AdminRequests.ChatHistoryRequest request, String traceId) {
        if (adminUserId == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        User admin = getRequiredUser(adminUserId);
        if (admin.getRole() != UserRole.ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "활성 관리자만 대화를 열람할 수 있습니다.");
        }
        if (request == null || roomId == null || roomId <= 0
                || (request.beforeId() != null && request.beforeId() <= 0)
                || (request.size() != null && (request.size() < 1 || request.size() > 100))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 범위를 확인하세요.");
        }
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        if (request.beforeId() != null) {
            var source = chatMessageRepository.findReportSourceById(request.beforeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "이전 메시지를 찾을 수 없습니다."));
            if (!Objects.equals(source.getRoomId(), roomId)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "이 방의 메시지 번호가 아닙니다.");
            }
        }
        int size = request.size() == null ? 50 : request.size();
        // Reuse the user's existing ID cursor order, without membership/read-receipt mutations.
        var rows = chatMessageRepository.findMessagesCursor(roomId, request.beforeId(), PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        var items = rows.stream().limit(size).map(this::toChatMessageItem).toList();
        LocalDateTime inspectedAt = adminAuditLogService.recordChatHistory(
                adminUserId, roomId, request.beforeId(), size, items, traceId);
        return new AdminDtos.ChatHistory(roomId, items,
                hasMore ? items.get(items.size() - 1).messageId() : null, hasMore, inspectedAt);
    }

    public AdminDtos.ChatMessageInspection inspectChatMessages(
            Long adminUserId,
            Long roomId,
            AdminRequests.ChatMessageInspectionRequest request,
            String traceId
    ) {
        if (adminUserId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "활성 관리자만 대화를 열람할 수 있습니다.");
        }
        User admin = getRequiredUser(adminUserId);
        if (admin.getRole() != UserRole.ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "활성 관리자만 대화를 열람할 수 있습니다.");
        }
        if (request == null || request.reason() == null
                || (request.page() != null && request.page() < 0)
                || (request.size() != null && (request.size() < 1 || request.size() > 100))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "열람 사유와 페이지 범위를 확인하세요.");
        }
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        LocalDateTime to = request.to() == null ? now : request.to();
        LocalDateTime from = request.from() == null ? to.minusHours(24) : request.from();
        if (from.isAfter(to) || Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "대화 조회 기간은 7일 이내여야 합니다.");
        }
        if (to.isAfter(now.plusMinutes(1))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "미래 시각의 대화는 조회할 수 없습니다.");
        }
        int page = safePage(request.page());
        int size = request.size() == null ? 50 : request.size();
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<AdminDtos.ChatMessageItem> messages = chatMessageRepository
                .findByRoomIdAndCreatedAtBetween(roomId, from, to, pageable)
                .map(this::toChatMessageItem);
        List<Long> messageIds = messages.getContent().stream()
                .map(AdminDtos.ChatMessageItem::messageId)
                .toList();
        int deletedCount = (int) messages.getContent().stream()
                .filter(AdminDtos.ChatMessageItem::deleted)
                .count();
        LocalDateTime inspectedAt = adminAuditLogService.recordChatMessageInspection(
                adminUserId,
                roomId,
                new AdminRequests.ChatMessageInspectionRequest(request.reason(), from, to, page, size),
                messageIds,
                deletedCount,
                from,
                to,
                traceId
        );
        return new AdminDtos.ChatMessageInspection(
                roomId,
                request.reason(),
                from,
                to,
                inspectedAt,
                AdminDtos.PageResponse.from(messages)
        );
    }

    public AdminDtos.UserDetail getUserDetail(Long userId) {
        User user = getRequiredUser(userId);
        return toUserDetail(user);
    }

    @Transactional
    public AdminDtos.UserDetail applyUserAction(Long adminUserId, Long userId, AdminRequests.UserActionRequest request) {
        if (request == null || request.action() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (request.reportId() != null) {
            if (request.reportId() <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            if (adminUserId == null) throw new BusinessException(ErrorCode.FORBIDDEN);
            User actor = getRequiredUser(adminUserId);
            if (actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            UserReport sourceReport = userReportRepository.findByIdForUpdate(request.reportId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
            if (!Objects.equals(sourceReport.getReportedUserId(), userId)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고 대상과 조치 대상이 일치하지 않습니다.");
            }
        }
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
            case REACTIVATE -> {
                if (user.getStatus() == UserStatus.DELETED) {
                    if (user.isEmailProvider()) {
                        throw new BusinessException(
                                ErrorCode.INVALID_REQUEST,
                                "이메일 로그인 탈퇴 계정은 비밀번호가 삭제되어 복구할 수 없습니다."
                        );
                    }
                    user.restoreDeletedSocialAccount();
                } else {
                    user.reactivate();
                }
            }
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
        AdminDtos.UserDetail detail = toUserDetail(getRequiredUser(userId));
        if (request.reportId() != null) {
            // This action commits independently of a later report edit, but its own
            // linked receipt must commit or roll back together with the user action.
            adminAuditLogService.recordReportUserAction(adminUserId, request.reportId(), userId, request, reason);
            return detail;
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.USER_ACTION_APPLIED,
                "USER",
                userId,
                "사용자 #" + userId + "에게 " + request.action().name() + " 조치를 적용했습니다.",
                reason,
                Map.of(
                        "action", request.action().name(),
                        "until", nullablePayloadValue(formatDateTime(request.until()))
                )
        );
        return detail;
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

    public AdminDtos.StatisticsOverview getStatisticsOverview() {
        return getStatisticsOverview(null);
    }

    public AdminDtos.StatisticsOverview getStatisticsOverview(Long adminUserId) {
        MainStatisticsResponse main = statisticsService.getMainStatistics();
        AdminDtos.StatisticsOverview response = new AdminDtos.StatisticsOverview(
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
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.DASHBOARD_VIEWED,
                "OPERATIONS",
                "statistics-overview",
                "관리자 통계 대시보드를 조회했습니다.",
                null,
                Map.of(
                        "totalRegisteredUsers", response.totalRegisteredUsers(),
                        "dailyActiveUsers", response.dailyActiveUsers(),
                        "openReports", response.openReports()
                )
        );
        return response;
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
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.NOTICE_BROADCASTED,
                "NOTICE",
                savedNotice.getId(),
                "운영 공지 \"" + request.title() + "\"를 발송했습니다.",
                null,
                Map.of(
                        "noticeId", savedNotice.getId(),
                        "recipients", recipientIds.size(),
                        "activeUsersOnly", activeUsersOnly,
                        "deeplink", nullablePayloadValue(request.deeplink())
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
                user.getEmail(),
                user.getVerifiedSchoolEmail(),
                deriveSchoolName(user.getPrimaryEmail()),
                user.getDeptName(),
                user.getNickname(),
                user.getName(),
                user.getStudentNum(),
                user.getLastNicknameChangedAt(),
                user.getRole(),
                user.getStatus(),
                user.getOnboardingStatus(),
                user.getUserProfile() != null ? user.getUserProfile().getGender() : null,
                toUserProfileDetail(user.getUserProfile()),
                user.getTickets(),
                user.getCreatedAt(),
                user.getLastActiveAt(),
                user.getDeletedAt(),
                user.getSuspendedUntil(),
                user.getRestrictedAt(),
                user.getRestrictedUntil(),
                user.getRestrictedReason(),
                user.isMatchingRestricted(),
                userReportRepository.countByReportedUserIdAndStatus(user.getId(), ReportStatus.OPEN),
                loadPurchaseHistories(user.getId()),
                loadSentRequestHistories(user.getId()),
                loadTicketUsageHistories(user.getId()),
                loadApiUsageHistories(user.getId())
        );
    }

    private AdminDtos.UserProfileDetail toUserProfileDetail(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new AdminDtos.UserProfileDetail(
                profile.getHeight(),
                profile.getAge(),
                profile.getMbti(),
                profile.getSmoking(),
                profile.getGender(),
                profile.getMilitary(),
                profile.getReligion(),
                profile.getResidence(),
                profile.getIntro(),
                profile.getInstagram(),
                profile.getProfileImagePath(),
                profile.getUpdatedAt()
        );
    }

    private AdminDtos.ChatRoomSummary toChatRoomSummary(ChatRoom room, Map<Long, User> users) {
        return new AdminDtos.ChatRoomSummary(
                room.getId(),
                room.getName(),
                room.getType(),
                room.getConnectionId(),
                room.getUser1Id(),
                getNickname(users.get(room.getUser1Id())),
                room.getUser2Id(),
                getNickname(users.get(room.getUser2Id())),
                room.getLastMessageAt(),
                chatRoomMemberRepository.countByChatRoomId(room.getId()),
                chatMessageRepository.countByRoomId(room.getId()),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    private AdminDtos.ChatRoomMemberItem toChatRoomMemberItem(ChatRoomMember member) {
        User user = member.getUser();
        return new AdminDtos.ChatRoomMemberItem(
                member.getId(),
                user.getId(),
                getNickname(user),
                member.getJoinedAt(),
                member.getHiddenAt() != null
        );
    }

    private AdminDtos.ChatMessageItem toChatMessageItem(ChatMessage message) {
        return new AdminDtos.ChatMessageItem(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getSenderNickname(),
                message.getDisplayContent(),
                message.getType(),
                message.isDeleted(),
                message.getDeletedAt(),
                message.getReadAt(),
                message.getCreatedAt()
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
