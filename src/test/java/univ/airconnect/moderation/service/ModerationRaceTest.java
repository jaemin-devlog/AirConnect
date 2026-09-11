package univ.airconnect.moderation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserBlock;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.dto.request.CreateUserReportRequest;
import univ.airconnect.moderation.dto.response.UserBlockCreateResponse;
import univ.airconnect.moderation.dto.response.UserReportResponse;
import univ.airconnect.moderation.infrastructure.ModerationProperties;
import univ.airconnect.moderation.repository.UserBlockRepository;
import univ.airconnect.matching.service.MatchingLifecycleService;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationRaceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserReportRepository userReportRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private MatchingLifecycleService matchingLifecycleService;

    private final ModerationProperties moderationProperties = new ModerationProperties();

    private UserReportService userReportService;

    private UserBlockService userBlockService;

    @BeforeEach
    void setUp() {
        userReportService = new UserReportService(userRepository, userReportRepository, moderationProperties);
        userBlockService = new UserBlockService(
                userRepository,
                userBlockRepository,
                chatRoomMemberRepository,
                matchingLifecycleService
        );
    }

    @Test
    @DisplayName("동시에 같은 신고가 2번 들어오면 1건만 저장되고 나머지는 중복 신고로 거절된다")
    void createReport_concurrentDuplicateReports_oneSavedOneRejected() throws Exception {
        moderationProperties.getReport().setDuplicateWindowMinutes(60L);

        Long reporterId = 1L;
        Long reportedId = 2L;
        User reporter = user(reporterId, "reporter");
        User reported = user(reportedId, "reported");

        AtomicInteger duplicateChecks = new AtomicInteger();
        AtomicReference<UserReport> storedReport = new AtomicReference<>();
        CountDownLatch firstSaveDone = new CountDownLatch(1);

        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(reportedId)).thenReturn(Optional.of(reported));
        when(userReportRepository.existsRecentDuplicate(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            int call = duplicateChecks.incrementAndGet();
            if (call > 1) {
                await(firstSaveDone, "first report save");
                return true;
            }
            return false;
        });
        when(userReportRepository.save(any(UserReport.class))).thenAnswer(invocation -> {
            UserReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 301L);
            storedReport.set(report);
            firstSaveDone.countDown();
            return report;
        });

        CreateUserReportRequest request = new CreateUserReportRequest();
        ReflectionTestUtils.setField(request, "reportedUserId", reportedId);
        ReflectionTestUtils.setField(request, "reportReason", ReportReasonCode.HARASSMENT);
        ReflectionTestUtils.setField(request, "sourceType", ReportSourceType.OTHER);
        ReflectionTestUtils.setField(request, "detail", "욕설 및 반복 메시지");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(task(startGate, () -> userReportService.createReport(reporterId, request))),
                    executor.submit(task(startGate, () -> userReportService.createReport(reporterId, request)))
            );

            startGate.countDown();

            int successCount = 0;
            int duplicateFailureCount = 0;
            for (Future<Object> future : futures) {
                try {
                    Object result = future.get(5, TimeUnit.SECONDS);
                    assertThat(result).isInstanceOf(UserReportResponse.class);
                    successCount++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    assertThat(cause).isInstanceOf(univ.airconnect.moderation.exception.ModerationException.class);
                    duplicateFailureCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(duplicateFailureCount).isEqualTo(1);
            assertThat(storedReport.get().getStatus()).isEqualTo(ReportStatus.OPEN);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("같은 사용자 차단 요청이 동시에 2번 들어와도 제재는 1번만 생성된다")
    void block_concurrentRequests_onlyOneSanctionCreated() throws Exception {
        Long blockerId = 11L;
        Long blockedId = 22L;
        User blocker = user(blockerId, "blocker");
        User blocked = user(blockedId, "blocked");

        AtomicInteger lookupCount = new AtomicInteger();
        AtomicReference<UserBlock> storedBlock = new AtomicReference<>();
        CountDownLatch firstSaveDone = new CountDownLatch(1);

        when(userRepository.findById(blockerId)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(blockedId)).thenReturn(Optional.of(blocked));
        when(userRepository.findByIdForTicketUpdate(blockerId)).thenReturn(Optional.of(blocker));
        when(userRepository.findByIdForTicketUpdate(blockedId)).thenReturn(Optional.of(blocked));
        when(userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerId, blockedId)).thenAnswer(invocation -> {
            int call = lookupCount.incrementAndGet();
            if (call > 1) {
                await(firstSaveDone, "first block save");
            }
            return Optional.ofNullable(storedBlock.get());
        });
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> {
            UserBlock block = invocation.getArgument(0);
            ReflectionTestUtils.setField(block, "id", 401L);
            storedBlock.set(block);
            firstSaveDone.countDown();
            return block;
        });
        lenient().when(chatRoomMemberRepository.findCommonPersonalRoomIds(blockerId, blockedId)).thenReturn(List.of());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<UserBlockCreateResponse>> futures = List.of(
                    executor.submit(task(startGate, () -> userBlockService.block(blockerId, blockedId))),
                    executor.submit(task(startGate, () -> userBlockService.block(blockerId, blockedId)))
            );

            startGate.countDown();

            int createdCount = 0;
            int alreadyExistsCount = 0;
            for (Future<UserBlockCreateResponse> future : futures) {
                UserBlockCreateResponse response = future.get(5, TimeUnit.SECONDS);
                if (response.isAlreadyBlocked()) {
                    alreadyExistsCount++;
                } else {
                    createdCount++;
                }
            }

            assertThat(createdCount).isEqualTo(1);
            assertThat(alreadyExistsCount).isEqualTo(1);
            assertThat(storedBlock.get()).isNotNull();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private <T> Callable<T> task(CountDownLatch startGate, Callable<T> delegate) {
        return () -> {
            await(startGate, "start gate");
            return delegate.call();
        };
    }

    private void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for " + label);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + label, e);
        }
    }

    private User user(Long id, String key) {
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId("social-" + key)
                .email(key + "@airconnect.test")
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(10)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
