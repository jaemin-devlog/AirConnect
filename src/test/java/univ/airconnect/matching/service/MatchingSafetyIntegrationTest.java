package univ.airconnect.matching.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;
import univ.airconnect.moderation.domain.entity.UserBlock;
import univ.airconnect.moderation.repository.UserBlockRepository;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.user.domain.*;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import({MatchingService.class, UserBlockPolicyService.class, TestNotificationConfig.class})
class MatchingSafetyIntegrationTest {

    @Autowired MatchingService matchingService;
    @Autowired UserRepository userRepository;
    @Autowired UserProfileRepository userProfileRepository;
    @Autowired MatchingConnectionRepository matchingConnectionRepository;
    @Autowired MatchingConnectRequestRepository connectRequestRepository;
    @Autowired MatchingExposureRepository matchingExposureRepository;
    @Autowired MatchingRecommendationRequestRepository recommendationRequestRepository;
    @Autowired UserBlockRepository userBlockRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean ChatService chatService;
    @MockitoBean AnalyticsService analyticsService;

    @Test
    void recommendationRequiresIdempotencyKey() {
        User requester = saveUserWithProfile(Gender.MALE, 10);

        assertThatThrownBy(() -> matchingService.recommend(requester.getId(), " "))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertThat(userRepository.findById(requester.getId()).orElseThrow().getTickets()).isEqualTo(10);
    }

    @Test
    void recommendationRetryWithSameKeyReturnsSameResultWithoutChargingAgain() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        saveUserWithProfile(Gender.FEMALE, 10);
        saveUserWithProfile(Gender.FEMALE, 10);
        String requestId = "r".repeat(100);

        var first = matchingService.recommend(requester.getId(), requestId);
        var retried = matchingService.recommend(requester.getId(), requestId);

        assertThat(retried.getRecommendationRequestId()).isEqualTo(requestId);
        assertThat(retried.getCandidates()).extracting(candidate -> candidate.getUserId())
                .containsExactlyElementsOf(first.getCandidates().stream().map(candidate -> candidate.getUserId()).toList());
        assertThat(retried.getUserTicketsRemaining()).isEqualTo(9);
        assertThat(userRepository.findById(requester.getId()).orElseThrow().getTickets()).isEqualTo(9);
        assertThat(recommendationRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void recommendationKeyCannotBeReusedForAnotherGenderMode() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        saveUserWithProfile(Gender.FEMALE, 10);
        String requestId = UUID.randomUUID().toString();

        matchingService.recommend(requester.getId(), requestId);

        assertThatThrownBy(() -> matchingService.recommendSameGender(requester.getId(), requestId))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void recommendationRetryRevalidatesRequesterAndCurrentCandidateEligibility() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User blockedCandidate = saveUserWithProfile(Gender.FEMALE, 10);
        User allowedCandidate = saveUserWithProfile(Gender.FEMALE, 10);
        String requestId = UUID.randomUUID().toString();

        var first = matchingService.recommend(requester.getId(), requestId);
        assertThat(first.getCandidates()).extracting(candidate -> candidate.getUserId())
                .containsExactlyInAnyOrder(blockedCandidate.getId(), allowedCandidate.getId());

        userBlockRepository.save(UserBlock.create(requester.getId(), blockedCandidate.getId()));
        var retried = matchingService.recommend(requester.getId(), requestId);

        assertThat(retried.getCandidates()).extracting(candidate -> candidate.getUserId())
                .containsExactly(allowedCandidate.getId());
        assertThat(userRepository.findById(requester.getId()).orElseThrow().getTickets()).isEqualTo(9);

        allowedCandidate.restrictMatching(null, "운영 제한");
        assertThat(matchingService.recommend(requester.getId(), requestId).getCandidates()).isEmpty();

        requester.restrictMatching(null, "운영 제한");
        assertThatThrownBy(() -> matchingService.recommend(requester.getId(), requestId))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCHING_RESTRICTED);
    }

    @Test
    void recommendationRetryRestoresExposureSoCandidateCanBeConnected() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User target = saveUserWithProfile(Gender.FEMALE, 10);
        String recommendationRequestId = UUID.randomUUID().toString();

        matchingService.recommend(requester.getId(), recommendationRequestId);
        matchingExposureRepository.deleteByUserId(requester.getId());
        matchingExposureRepository.flush();
        assertThat(matchingExposureRepository.existsByUserIdAndCandidateUserId(requester.getId(), target.getId()))
                .isFalse();

        var retried = matchingService.recommend(requester.getId(), recommendationRequestId);
        assertThat(retried.getCandidates()).extracting(candidate -> candidate.getUserId())
                .containsExactly(target.getId());
        assertThat(matchingExposureRepository.existsByUserIdAndCandidateUserId(requester.getId(), target.getId()))
                .isTrue();

        matchingService.connect(requester.getId(), target.getId(), UUID.randomUUID().toString());
        assertThat(matchingConnectionRepository
                .findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(requester.getId(), target.getId()))
                .singleElement()
                .extracting(MatchingConnection::getStatus)
                .isEqualTo(ConnectionStatus.PENDING);
    }

    @Test
    void connectRetryAfterRejectionDoesNotCreateOrChargeAgain() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User receiver = saveUserWithProfile(Gender.FEMALE, 10);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), receiver.getId()));
        String connectRequestId = UUID.randomUUID().toString();

        var first = matchingService.connect(requester.getId(), receiver.getId(), connectRequestId);
        MatchingConnection connection = matchingConnectionRepository
                .findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(requester.getId(), receiver.getId())
                .get(0);
        matchingService.rejectRequest(receiver.getId(), connection.getId());

        var retried = matchingService.connect(requester.getId(), receiver.getId(), connectRequestId);

        assertThat(retried.getChatRoomId()).isEqualTo(first.getChatRoomId());
        assertThat(retried.isAlreadyConnected()).isEqualTo(first.isAlreadyConnected());
        assertThat(matchingConnectionRepository
                .findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(requester.getId(), receiver.getId()))
                .hasSize(1);
        assertThat(userRepository.findById(requester.getId()).orElseThrow().getTickets()).isEqualTo(8);
        assertThat(connectRequestRepository.count()).isEqualTo(1);

        User anotherTarget = saveUserWithProfile(Gender.FEMALE, 10);
        assertThatThrownBy(() -> matchingService.connect(requester.getId(), anotherTarget.getId(), connectRequestId))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void restrictedCandidateIsExcludedAndRestrictedRequesterCannotBeAccepted() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User restrictedCandidate = saveUserWithProfile(Gender.FEMALE, 10);
        restrictedCandidate.restrictMatching(null, "운영 제한");
        User allowedCandidate = saveUserWithProfile(Gender.FEMALE, 10);

        var recommendation = matchingService.recommend(requester.getId(), UUID.randomUUID().toString());
        assertThat(recommendation.getCandidates()).extracting(candidate -> candidate.getUserId())
                .containsExactly(allowedCandidate.getId())
                .doesNotContain(restrictedCandidate.getId());

        MatchingConnection pending = matchingConnectionRepository.save(
                MatchingConnection.createPending(restrictedCandidate.getId(), requester.getId())
        );
        assertThatThrownBy(() -> matchingService.acceptRequest(requester.getId(), pending.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCHING_RESTRICTED);
        assertThat(pending.getStatus()).isEqualTo(ConnectionStatus.PENDING);
    }

    @Test
    void oldResponseCannotChangeNewRequestRound() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User receiver = saveUserWithProfile(Gender.FEMALE, 10);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), receiver.getId()));
        MatchingConnection oldRequest = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), receiver.getId())
        );

        matchingService.rejectRequest(receiver.getId(), oldRequest.getId());
        matchingService.connect(requester.getId(), receiver.getId());
        MatchingConnection newRequest = matchingConnectionRepository
                .findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(requester.getId(), receiver.getId())
                .stream()
                .filter(connection -> connection.getStatus() == ConnectionStatus.PENDING)
                .findFirst()
                .orElseThrow();

        assertThat(newRequest.getId()).isNotEqualTo(oldRequest.getId());
        assertThatThrownBy(() -> matchingService.rejectRequest(receiver.getId(), oldRequest.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);
        assertThat(matchingConnectionRepository.findById(newRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.PENDING);
    }

    @Test
    void blockedCounterpartIsHiddenFromPendingRequestList() {
        User requester = saveUserWithProfile(Gender.MALE, 10);
        User receiver = saveUserWithProfile(Gender.FEMALE, 10);
        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));
        userBlockRepository.save(UserBlock.create(receiver.getId(), requester.getId()));

        assertThat(matchingService.getRequests(receiver.getId()).getReceived()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentAcceptAndRejectAllowOnlyOneDecision() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            User requester = saveUserWithProfile(Gender.MALE, 10);
            User receiver = saveUserWithProfile(Gender.FEMALE, 10);
            MatchingConnection connection = matchingConnectionRepository.save(
                    MatchingConnection.createPending(requester.getId(), receiver.getId())
            );
            return new Long[]{receiver.getId(), connection.getId()};
        });

        when(chatService.createOrGetPersonalRoomForConnection(any(), any(), any(), any()))
                .thenReturn(ChatRoomResponse.builder()
                        .id(999L)
                        .name("1:1")
                        .type(ChatRoomType.PERSONAL)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .build());

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Decision> accept = () -> decide("ACCEPT", start,
                    () -> matchingService.acceptRequest(ids[0], ids[1]));
            Callable<Decision> reject = () -> decide("REJECT", start,
                    () -> matchingService.rejectRequest(ids[0], ids[1]));
            var acceptFuture = executor.submit(accept);
            var rejectFuture = executor.submit(reject);
            start.countDown();

            List<Decision> results = List.of(
                    acceptFuture.get(10, TimeUnit.SECONDS),
                    rejectFuture.get(10, TimeUnit.SECONDS)
            );
            assertThat(results).filteredOn(Decision::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success())
                    .extracting(Decision::errorCode)
                    .containsExactly(MatchingErrorCode.INVALID_REQUEST);

            ConnectionStatus stored = tx.execute(status ->
                    matchingConnectionRepository.findById(ids[1]).orElseThrow().getStatus()
            );
            String successfulAction = results.stream().filter(Decision::success).findFirst().orElseThrow().action();
            assertThat(stored).isEqualTo("ACCEPT".equals(successfulAction)
                    ? ConnectionStatus.ACCEPTED
                    : ConnectionStatus.REJECTED);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void oppositeDirectionConnectsCreateOnePendingRequestAndChargeOneRequester() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] userIds = tx.execute(status -> {
            User first = saveUserWithProfile(Gender.MALE, 10);
            User second = saveUserWithProfile(Gender.FEMALE, 10);
            matchingExposureRepository.save(MatchingExposure.create(first.getId(), second.getId()));
            matchingExposureRepository.save(MatchingExposure.create(second.getId(), first.getId()));
            return new Long[]{first.getId(), second.getId()};
        });

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Decision> firstToSecond = () -> decide("FIRST", start,
                    () -> matchingService.connect(userIds[0], userIds[1]));
            Callable<Decision> secondToFirst = () -> decide("SECOND", start,
                    () -> matchingService.connect(userIds[1], userIds[0]));
            var firstFuture = executor.submit(firstToSecond);
            var secondFuture = executor.submit(secondToFirst);
            start.countDown();

            List<Decision> results = List.of(
                    firstFuture.get(10, TimeUnit.SECONDS),
                    secondFuture.get(10, TimeUnit.SECONDS)
            );
            assertThat(results).filteredOn(Decision::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success())
                    .extracting(Decision::errorCode)
                    .containsExactly(MatchingErrorCode.ALREADY_CONNECTED);

            tx.executeWithoutResult(status -> {
                List<MatchingConnection> connections = matchingConnectionRepository
                        .findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(userIds[0], userIds[1]);
                assertThat(connections).hasSize(1);
                assertThat(connections.get(0).getStatus()).isEqualTo(ConnectionStatus.PENDING);
                int totalTickets = userRepository.findById(userIds[0]).orElseThrow().getTickets()
                        + userRepository.findById(userIds[1]).orElseThrow().getTickets();
                assertThat(totalTickets).isEqualTo(18);
            });
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Decision decide(String action, CountDownLatch start, Runnable operation) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            operation.run();
            return new Decision(action, true, null);
        } catch (MatchingException exception) {
            return new Decision(action, false, exception.getErrorCode());
        }
    }

    private User saveUserWithProfile(Gender gender, int tickets) {
        String socialId = "safety-" + UUID.randomUUID();
        User user = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(socialId)
                .email(socialId + "@example.com")
                .nickname("safety")
                .deptName("Computer Science")
                .studentNum(20240001)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build());
        userProfileRepository.save(UserProfile.create(
                user, 170, 24, "INTJ", "NO", gender,
                MilitaryStatus.NOT_APPLICABLE, "NONE", "Seoul", "hello", "instagram"
        ));
        return user;
    }

    private record Decision(String action, boolean success, MatchingErrorCode errorCode) {
    }
}
