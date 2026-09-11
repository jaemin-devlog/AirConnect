package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminNoticeRepository adminNoticeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserReportRepository userReportRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private IapOrderRepository iapOrderRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private StatisticsService statisticsService;
    @Mock
    private AdminAuditLogService adminAuditLogService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                adminNoticeRepository,
                userRepository,
                userReportRepository,
                matchingConnectionRepository,
                iapOrderRepository,
                ticketLedgerRepository,
                analyticsEventRepository,
                chatRoomRepository,
                chatRoomMemberRepository,
                chatMessageRepository,
                userService,
                notificationService,
                statisticsService,
                new ObjectMapper(),
                adminAuditLogService
        );
    }

    @Test
    void adjustTickets_updatesBalanceAndCreatesLedger() {
        User user = user(1L, 10);
        User admin = adminUser(999L, "운영팀");
        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdForTicketUpdate(1L)).thenReturn(Optional.of(user));
        var operations = mock(AdminTicketAdjustmentRepository.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(operations.saveAndFlush(any(AdminTicketAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketLedgerRepository.save(any(TicketLedger.class))).thenAnswer(invocation -> {
            TicketLedger entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", 100L);
            return entry;
        });
        var adjustmentService = new AdminTicketAdjustmentService(operations, userRepository,
                ticketLedgerRepository, notificationService, adminAuditLogService, new ObjectMapper(), transactionManager);
        String operationId = UUID.randomUUID().toString();
        AdminDtos.TicketAdjustmentResult response = adjustmentService.adjust(
                999L,
                new AdminRequests.TicketAdjustmentRequest(operationId, 1L, 5, "bug compensation")
        );

        ArgumentCaptor<TicketLedger> captor = ArgumentCaptor.forClass(TicketLedger.class);
        ArgumentCaptor<NotificationService.CreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(ticketLedgerRepository).save(captor.capture());
        verify(notificationService).createAndEnqueue(notificationCaptor.capture());
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.operationId()).isEqualTo(operationId);
        assertThat(response.afterTickets()).isEqualTo(15);
        assertThat(response.ledgerId()).isEqualTo(100L);
        assertThat(captor.getValue().getChangeAmount()).isEqualTo(5);
        assertThat(captor.getValue().getAfterAmount()).isEqualTo(15);
        assertThat(captor.getValue().getReason()).isEqualTo("ADMIN:bug compensation");
        assertThat(captor.getValue().getRefId()).isEqualTo("admin-adjustment:" + operationId);
        assertThat(notificationCaptor.getValue().userId()).isEqualTo(1L);
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);
        assertThat(notificationCaptor.getValue().title()).isEqualTo("운영팀");
        assertThat(notificationCaptor.getValue().body()).contains("티켓 5개가 지급되었습니다.");
        assertThat(notificationCaptor.getValue().body()).contains("bug compensation");
    }

    @Test
    void applyUserAction_restrictMatching_marksUserRestricted() {
        User user = user(2L, 10);
        User admin = adminUser(999L, "운영 매니저");
        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForTicketUpdate(2L)).thenReturn(Optional.of(user));
        when(userReportRepository.countByReportedUserIdAndStatus(2L, ReportStatus.OPEN)).thenReturn(0L);

        AdminDtos.UserDetail response = adminService.applyUserAction(
                999L,
                2L,
                new AdminRequests.UserActionRequest(
                        AdminRequests.UserActionType.RESTRICT_MATCHING,
                        "abuse",
                        LocalDateTime.now().plusDays(3)
                )
        );

        ArgumentCaptor<NotificationService.CreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService).createAndEnqueue(notificationCaptor.capture());
        assertThat(user.isMatchingRestricted()).isTrue();
        verify(matchingConnectionRepository).cancelPendingForUser(org.mockito.ArgumentMatchers.eq(2L), any());
        assertThat(response.restrictedReason()).isEqualTo("abuse");
        assertThat(notificationCaptor.getValue().userId()).isEqualTo(2L);
        assertThat(notificationCaptor.getValue().title()).isEqualTo("운영 매니저");
        assertThat(notificationCaptor.getValue().body()).contains("회원님의 매칭 기능이 제한되었습니다.");
        assertThat(notificationCaptor.getValue().body()).contains("abuse");
    }

    @Test
    void updateReportStatus_inReview_notifiesReporter() {
        User admin = adminUser(999L, "운영팀");
        User reporter = user(10L, 10);
        ReflectionTestUtils.setField(reporter, "nickname", "신고자");
        User reported = user(20L, 10);
        ReflectionTestUtils.setField(reported, "nickname", "피신고자");
        UserReport report = report(10L, 20L);
        ReflectionTestUtils.setField(report, "version", 0L);

        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(userReportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(userRepository.findById(10L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(20L)).thenReturn(Optional.of(reported));

        AdminDtos.ReportDetail response = reportService().update(
                999L,
                1L,
                new AdminRequests.ReportStatusUpdateRequest(0L, ReportStatus.IN_REVIEW, null, null)
        );

        ArgumentCaptor<NotificationService.CreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService).createAndEnqueue(notificationCaptor.capture());
        assertThat(response.record().status()).isEqualTo(ReportStatus.IN_REVIEW);
        assertThat(notificationCaptor.getValue().userId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().title()).isEqualTo("운영팀");
        assertThat(notificationCaptor.getValue().body()).isEqualTo("신고가 검토중입니다. 빠른 시일 내에 처리됩니다.");
    }

    @Test
    void updateReportStatus_resolved_includesResolutionReason() {
        User admin = adminUser(999L, "운영팀");
        User reporter = user(10L, 10);
        User reported = user(20L, 10);
        UserReport report = report(10L, 20L);
        ReflectionTestUtils.setField(report, "version", 0L);

        when(userRepository.findById(999L)).thenReturn(Optional.of(admin));
        when(userReportRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(report));
        when(userRepository.findById(10L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(20L)).thenReturn(Optional.of(reported));

        reportService().update(
                999L,
                2L,
                new AdminRequests.ReportStatusUpdateRequest(0L, ReportStatus.RESOLVED,
                        "internal-only fixture", "대상이 30일 정지 처리되었습니다.")
        );

        ArgumentCaptor<NotificationService.CreateCommand> notificationCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService).createAndEnqueue(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().body())
                .isEqualTo("신고가 처리되었습니다. 대상이 30일 정지 처리되었습니다.");
        assertThat(notificationCaptor.getValue().payloadJson()).doesNotContain("internal-only fixture");
    }

    @Test
    void applyUserAction_reactivateDeletedSocialAccount_restoresOnlyLoginIdentity() {
        User user = user(3L, 7);
        String retainedSocialId = user.getSocialId();
        user.anonymizeForDeletion();
        user.markDeleted();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForTicketUpdate(3L)).thenReturn(Optional.of(user));

        AdminDtos.UserDetail response = adminService.applyUserAction(
                999L,
                3L,
                new AdminRequests.UserActionRequest(
                        AdminRequests.UserActionType.REACTIVATE,
                        "본인 요청 확인 후 계정 재사용 허용",
                        null
                )
        );

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.onboardingStatus()).isEqualTo(OnboardingStatus.BASIC);
        assertThat(response.deletedAt()).isNull();
        assertThat(response.email()).isNull();
        assertThat(response.nickname()).isNull();
        assertThat(user.getSocialId()).isEqualTo(retainedSocialId);
        assertThat(user.getTickets()).isEqualTo(7);
    }

    @Test
    void applyUserAction_reactivateDeletedEmailAccount_isRejectedBecausePasswordWasDeleted() {
        User user = User.createEmailUser("withdrawn@example.test", "password-hash");
        ReflectionTestUtils.setField(user, "id", 4L);
        user.anonymizeForDeletion();
        user.markDeleted();
        when(userRepository.findByIdForTicketUpdate(4L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminService.applyUserAction(
                999L,
                4L,
                new AdminRequests.UserActionRequest(
                        AdminRequests.UserActionType.REACTIVATE,
                        "복구 요청",
                        null
                )
        ))
                .isInstanceOf(univ.airconnect.global.error.BusinessException.class)
                .hasMessageContaining("비밀번호가 삭제되어 복구할 수 없습니다");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
    }

    private AdminReportService reportService() {
        return new AdminReportService(userReportRepository, userRepository, chatMessageRepository,
                chatRoomRepository, chatRoomMemberRepository, matchingConnectionRepository,
                mock(AdminAuditLogRepository.class), adminAuditLogService, notificationService,
                new ObjectMapper(), adminService);
    }

    @Test
    void broadcastNotice_sendsSystemAnnouncementToActiveUsers() {
        when(userRepository.findIdsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(10L, 20L));
        when(adminNoticeRepository.save(any())).thenAnswer(invocation -> {
            AdminNotice notice = invocation.getArgument(0);
            ReflectionTestUtils.setField(notice, "id", 77L);
            return notice;
        });

        AdminDtos.NoticeBroadcastResult response = adminService.broadcastNotice(
                999L,
                new AdminRequests.NoticeBroadcastRequest("공지", "점검 예정", "airconnect://notice", true)
        );

        verify(notificationService, times(2)).createAndEnqueue(any());
        assertThat(response.noticeId()).isEqualTo(77L);
        assertThat(response.recipients()).isEqualTo(2);
    }

    @Test
    void getNotices_returnsPagedSummaries() {
        AdminNotice notice = AdminNotice.create(999L, "공지", "본문", "airconnect://notice", true, 12);
        ReflectionTestUtils.setField(notice, "id", 7L);

        when(adminNoticeRepository.findAllByOrderByCreatedAtDescIdDesc(any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(notice)));

        AdminDtos.PageResponse<AdminDtos.NoticeSummary> response = adminService.getNotices(0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).noticeId()).isEqualTo(7L);
        assertThat(response.items().get(0).recipientCount()).isEqualTo(12);
    }

    @Test
    void getNoticeDetail_returnsSavedNotice() {
        AdminNotice notice = AdminNotice.create(999L, "공지", "본문", "airconnect://notice", true, 12);
        ReflectionTestUtils.setField(notice, "id", 8L);
        when(adminNoticeRepository.findById(8L)).thenReturn(Optional.of(notice));

        AdminDtos.NoticeDetail detail = adminService.getNoticeDetail(8L);

        assertThat(detail.noticeId()).isEqualTo(8L);
        assertThat(detail.body()).isEqualTo("본문");
        assertThat(detail.recipientCount()).isEqualTo(12);
    }

    @Test
    void getUserDetail_includesRecentHistories() {
        User user = user(5L, 10);
        user.updateVerifiedSchoolEmail("student@school.ac.kr");
        LocalDateTime nicknameChangedAt = LocalDateTime.of(2026, 9, 1, 12, 30);
        ReflectionTestUtils.setField(user, "lastNicknameChangedAt", nicknameChangedAt);
        UserProfile profile = UserProfile.create(
                user, 178, 24, "ENFP", "NON_SMOKER", Gender.MALE,
                MilitaryStatus.COMPLETED, "무교", "서울", "반갑습니다", "airconnect_member"
        );
        profile.updateProfileImagePath("profile-5.png");
        ReflectionTestUtils.setField(user, "userProfile", profile);
        User target = user(6L, 20);
        ReflectionTestUtils.setField(target, "nickname", "상대방");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userReportRepository.countByReportedUserIdAndStatus(5L, ReportStatus.OPEN)).thenReturn(2L);
        when(iapOrderRepository.findTop20ByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(iapOrder(5L)));
        when(matchingConnectionRepository.findTop20ByRequesterIdOrderByRecentDesc(5L)).thenReturn(List.of(connection(100L, 5L, 6L)));
        when(userRepository.findAllByIdWithProfile(java.util.Set.of(6L))).thenReturn(List.of(target));
        when(ticketLedgerRepository.findTop20UsageHistoryByUserId(5L)).thenReturn(List.of(ticketUsage(5L)));
        when(analyticsEventRepository.findTop20ByUserIdOrderByOccurredAtDescIdDesc(5L)).thenReturn(List.of(apiEvent(5L)));

        AdminDtos.UserDetail detail = adminService.getUserDetail(5L);

        assertThat(detail.email()).isEqualTo("student@school.ac.kr");
        assertThat(detail.socialEmail()).isEqualTo("u5@airconnect.test");
        assertThat(detail.verifiedSchoolEmail()).isEqualTo("student@school.ac.kr");
        assertThat(detail.lastNicknameChangedAt()).isEqualTo(nicknameChangedAt);
        assertThat(detail.profile()).isNotNull();
        assertThat(detail.profile().height()).isEqualTo(178);
        assertThat(detail.profile().mbti()).isEqualTo("ENFP");
        assertThat(detail.profile().instagram()).isEqualTo("airconnect_member");
        assertThat(detail.profile().profileImagePath()).isEqualTo("profile-5.png");
        assertThat(detail.matchingRestricted()).isFalse();
        assertThat(new ObjectMapper().findAndRegisterModules().valueToTree(detail).toString())
                .doesNotContain("passwordHash", "iosAppAccountToken", "refreshToken", "accessToken");
        assertThat(detail.openReportCount()).isEqualTo(2L);
        assertThat(detail.purchaseHistories()).hasSize(1);
        assertThat(detail.purchaseHistories().get(0).productId()).isEqualTo("ticket_10");
        assertThat(detail.sentRequestHistories()).hasSize(1);
        assertThat(detail.sentRequestHistories().get(0).targetNickname()).isEqualTo("상대방");
        assertThat(detail.ticketUsageHistories()).hasSize(1);
        assertThat(detail.ticketUsageHistories().get(0).usedAmount()).isEqualTo(2);
        assertThat(detail.apiUsageHistories()).hasSize(1);
        assertThat(detail.apiUsageHistories().get(0).type()).isEqualTo(AnalyticsEventType.USER_LOGGED_IN);
    }

    @Test
    void getStatisticsOverview_combinesExistingStatsAndTicketData() {
        MainStatisticsResponse main = MainStatisticsResponse.builder()
                .totalRegisteredUsers(100)
                .dailyActiveUsers(25)
                .genderRatio(MainStatisticsResponse.GenderRatio.builder()
                        .maleUsers(40)
                        .femaleUsers(50)
                        .unknownUsers(10)
                        .malePercentage(44)
                        .femalePercentage(56)
                        .build())
                .totalMatchSuccessCount(13)
                .generatedAt(LocalDateTime.now())
                .build();
        when(statisticsService.getMainStatistics()).thenReturn(main);
        when(statisticsService.getDepartmentRankings()).thenReturn(List.of(
                DepartmentRankingResponse.builder()
                        .rank(1)
                        .deptName("컴퓨터공학과")
                        .requestCount(12)
                        .build()
        ));
        when(ticketLedgerRepository.sumGrantedTickets()).thenReturn(200L);
        when(ticketLedgerRepository.sumConsumedTickets()).thenReturn(150L);
        when(userReportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(3L);

        AdminDtos.StatisticsOverview response = adminService.getStatisticsOverview();

        assertThat(response.totalRegisteredUsers()).isEqualTo(100);
        assertThat(response.genderRatio()).isNotNull();
        assertThat(response.genderRatio().maleUsers()).isEqualTo(40);
        assertThat(response.topRequestedDepartments()).hasSize(1);
        assertThat(response.topRequestedDepartments().get(0).deptName()).isEqualTo("컴퓨터공학과");
        assertThat(response.grantedTickets()).isEqualTo(200L);
        assertThat(response.consumedTickets()).isEqualTo(150L);
        assertThat(response.openReports()).isEqualTo(3L);
    }

    private User user(Long id, int tickets) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("social-" + id)
                .email("u" + id + "@airconnect.test")
                .tickets(tickets)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User adminUser(Long id, String name) {
        User user = User.builder()
                .provider(SocialProvider.EMAIL)
                .socialId("admin-" + id + "@airconnect.test")
                .email("admin-" + id + "@airconnect.test")
                .name(name)
                .role(UserRole.ADMIN)
                .tickets(10)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserReport report(Long reporterUserId, Long reportedUserId) {
        UserReport report = UserReport.createReceived(
                reporterUserId,
                reportedUserId,
                ReportReasonCode.HARASSMENT,
                "신고 상세",
                ReportSourceType.PROFILE,
                "profile-1"
        );
        ReflectionTestUtils.setField(report, "id", 1L);
        return report;
    }

    private IapOrder iapOrder(Long userId) {
        IapOrder order = IapOrder.createPending(
                userId,
                IapStore.APPLE,
                "ticket_10",
                "tx-1",
                "origin-tx-1",
                null,
                "order-1",
                "account-token",
                IapEnvironment.SANDBOX,
                "hash",
                "{}"
        );
        order.markGranted(10, 0, 10);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    private MatchingConnection connection(Long id, Long requesterId, Long targetUserId) {
        MatchingConnection connection = MatchingConnection.createPending(requesterId, targetUserId);
        ReflectionTestUtils.setField(connection, "id", id);
        return connection;
    }

    private TicketLedger ticketUsage(Long userId) {
        TicketLedger ledger = TicketLedger.consumeForMatchingConnect(userId, 2, 10, 8, "ref-1");
        ReflectionTestUtils.setField(ledger, "id", 11L);
        return ledger;
    }

    private AnalyticsEvent apiEvent(Long userId) {
        AnalyticsEvent event = AnalyticsEvent.server(
                userId,
                AnalyticsEventType.USER_LOGGED_IN,
                LocalDateTime.now(),
                "{\"provider\":\"APPLE\"}"
        );
        ReflectionTestUtils.setField(event, "id", 21L);
        return event;
    }
}
