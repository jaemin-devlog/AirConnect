package univ.airconnect.matching.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.matching.dto.response.MatchingConnectResponse;
import univ.airconnect.matching.dto.response.MatchingRecommendationResponse;
import univ.airconnect.matching.dto.response.MatchingRequestResponse;
import univ.airconnect.matching.dto.response.MatchingRequestsResponse;
import univ.airconnect.matching.dto.response.MatchingResponseResponse;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.moderation.domain.entity.UserBlock;
import univ.airconnect.moderation.repository.UserBlockRepository;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@DataJpaTest
@ActiveProfiles("test")
@Import({MatchingService.class, UserBlockPolicyService.class, TestNotificationConfig.class})
class MatchingServiceTest {

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private MatchingExposureRepository matchingExposureRepository;

    @Autowired
    private MatchingConnectionRepository matchingConnectionRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("2 candidates cost 1 ticket")
    void recommend_returnsTwoAndConsumesOneTicket() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User c2 = saveUserWithProfile("u3", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getCandidates()).extracting("userId").containsExactlyInAnyOrder(c1.getId(), c2.getId());
        assertThat(reloaded.getTickets()).isEqualTo(99);
    }

    @Test
    @DisplayName("recommendation retry returns the current ticket balance")
    void recommend_retryReturnsCurrentTicketBalance() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        saveUserWithProfile("u2", Gender.FEMALE, 100);
        saveUserWithProfile("u3", Gender.FEMALE, 100);
        String requestKey = UUID.randomUUID().toString();

        MatchingRecommendationResponse first = matchingService.recommend(requester.getId(), requestKey);
        requester.consumeTickets(5);
        userRepository.flush();
        MatchingRecommendationResponse retried = matchingService.recommend(requester.getId(), requestKey);

        assertThat(first.getUserTicketsRemaining()).isEqualTo(99);
        assertThat(retried.getUserTicketsRemaining()).isEqualTo(94);
    }

    @Test
    @DisplayName("1 candidate does not cost a ticket")
    void recommend_returnsOneAndDoesNotConsumeTicket() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getCandidates()).extracting("userId").containsExactly(c1.getId());
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("0 candidates does not cost a ticket")
    void recommend_noCandidate_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.getCount()).isEqualTo(0);
        assertThat(response.getCandidates()).isEmpty();
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("recommend failure does not cost a ticket")
    void recommend_exception_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 0);
        saveUserWithProfile("u2", Gender.FEMALE, 100);

        assertThatThrownBy(() -> matchingService.recommend(requester.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INSUFFICIENT_TICKETS);

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(0);
    }

    @Test
    @DisplayName("no duplicates appear within one cycle")
    void recommend_noDuplicatesWithinCycle() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User c2 = saveUserWithProfile("u3", Gender.FEMALE, 100);
        User c3 = saveUserWithProfile("u4", Gender.FEMALE, 100);

        MatchingRecommendationResponse first = matchingService.recommend(requester.getId());
        MatchingRecommendationResponse second = matchingService.recommend(requester.getId());

        List<Long> allShown = new ArrayList<>(first.getCandidates().stream().map(MatchingCandidateResponse::getUserId).toList());
        allShown.addAll(second.getCandidates().stream().map(MatchingCandidateResponse::getUserId).toList());

        assertThat(allShown).doesNotHaveDuplicates();
        assertThat(allShown).contains(c1.getId(), c2.getId(), c3.getId());
        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(99);
    }

    @Test
    @DisplayName("after cycle exhaustion recommendations reset automatically and consume a ticket when two are returned")
    void recommend_resetsAfterCycleAndConsumesTicketWhenTwoCandidatesReturned() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User c2 = saveUserWithProfile("u3", Gender.FEMALE, 100);

        MatchingRecommendationResponse first = matchingService.recommend(requester.getId());
        MatchingRecommendationResponse second = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(first.getCandidates()).extracting("userId").containsExactlyInAnyOrder(c1.getId(), c2.getId());
        assertThat(second.getCount()).isEqualTo(2);
        assertThat(second.getCandidates()).extracting("userId").containsExactlyInAnyOrder(c1.getId(), c2.getId());
        assertThat(reloaded.getTickets()).isEqualTo(98);
    }

    @Test
    @DisplayName("outgoing pending match is excluded from recommendations")
    void recommend_excludesMyPending() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);

        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), target.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId").doesNotContain(target.getId());
    }

    @Test
    @DisplayName("incoming pending match is excluded from recommendations")
    void recommend_excludesIncomingPending() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User other = saveUserWithProfile("u2", Gender.FEMALE, 100);

        matchingConnectionRepository.save(MatchingConnection.createPending(other.getId(), requester.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId").doesNotContain(other.getId());
    }

    @Test
    @DisplayName("active chat is excluded while rejected or closed chat can reappear")
    void recommend_exclusionAndReexposurePolicy() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User activeChatUser = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User rejectedUser = saveUserWithProfile("u3", Gender.FEMALE, 100);
        User closedChatUser = saveUserWithProfile("u4", Gender.FEMALE, 100);

        MatchingConnection activeConn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), activeChatUser.getId())
        );
        ChatRoom activeRoom = chatRoomRepository.save(ChatRoom.create("r1", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(activeRoom, requester));
        chatRoomMemberRepository.save(ChatRoomMember.create(activeRoom, activeChatUser));
        activeConn.accept(activeRoom.getId());

        MatchingConnection rejectedConn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), rejectedUser.getId())
        );
        rejectedConn.reject();

        MatchingConnection closedConn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), closedChatUser.getId())
        );
        ChatRoom closedRoom = chatRoomRepository.save(ChatRoom.create("r2", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(closedRoom, requester));
        closedConn.accept(closedRoom.getId());

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId")
                .doesNotContain(activeChatUser.getId())
                .contains(rejectedUser.getId(), closedChatUser.getId());
    }

    @Test
    @DisplayName("connect success costs two tickets")
    void connect_success_consumesTwoTickets() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));

        MatchingConnectResponse response = matchingService.connect(requester.getId(), target.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.isAlreadyConnected()).isFalse();
        assertThat(reloaded.getTickets()).isEqualTo(98);
    }

    @Test
    @DisplayName("duplicate pending request does not cost tickets")
    void connect_pendingDuplicate_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));
        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), target.getId()));

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_CONNECTED);

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("already connected users do not spend tickets again")
    void connect_activeConnection_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));

        MatchingConnection conn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), target.getId())
        );
        ChatRoom room = chatRoomRepository.save(ChatRoom.create("r", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, requester));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, target));
        conn.accept(room.getId());

        MatchingConnectResponse response = matchingService.connect(requester.getId(), target.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.isAlreadyConnected()).isTrue();
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("an active connection is returned before ticket and exposure validation")
    void connect_activeConnectionWithNoTickets_returnsExistingRoom() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 0);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), target.getId())
        );
        ChatRoom room = chatRoomRepository.save(ChatRoom.create("r", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, requester));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, target));
        conn.accept(room.getId());

        MatchingConnectResponse response = matchingService.connect(
                requester.getId(), target.getId(), UUID.randomUUID().toString()
        );

        assertThat(response.isAlreadyConnected()).isTrue();
        assertThat(response.getConnectionId()).isEqualTo(conn.getId());
        assertThat(response.getChatRoomId()).isEqualTo(room.getId());
        assertThat(response.getUserTicketsRemaining()).isZero();
    }

    @Test
    @DisplayName("only the requester can cancel a pending request")
    void cancelRequest_requesterCancelsPendingRequest() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), receiver.getId())
        );

        MatchingResponseResponse response = matchingService.cancelRequest(requester.getId(), conn.getId());

        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.CANCELLED);
        assertThatThrownBy(() -> matchingService.cancelRequest(receiver.getId(), conn.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("connect rejects a target whose onboarding is no longer complete")
    void connect_revalidatesTargetOnboarding() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));
        target.resetOnboarding();
        userRepository.flush();

        assertThatThrownBy(() -> matchingService.connect(
                requester.getId(), target.getId(), UUID.randomUUID().toString()
        ))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_TARGET);
    }

    @Test
    @DisplayName("connect idempotency retry still enforces the current requester eligibility")
    void connect_retryRevalidatesRequesterEligibility() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));
        String requestKey = UUID.randomUUID().toString();
        matchingService.connect(requester.getId(), target.getId(), requestKey);
        requester.restrictMatching(null, "admin restriction");
        userRepository.flush();

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId(), requestKey))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCHING_RESTRICTED);
    }

    @Test
    @DisplayName("requesting a non-exposed candidate fails without spending tickets")
    void connect_notExposed_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.CANDIDATE_NOT_EXPOSED);

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("receiver can accept a request")
    void accept_receiverAllowed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        Mockito.when(chatService.createOrGetPersonalRoomForConnection(any(), any(), any(), any()))
                .thenReturn(ChatRoomResponse.builder()
                        .id(99L)
                        .name("1:1")
                        .type(ChatRoomType.PERSONAL)
                        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .build());

        MatchingResponseResponse response = matchingService.acceptRequest(receiver.getId(), conn.getId());

        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
        assertThat(response.getChatRoomId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("receiver can reject a request")
    void reject_receiverAllowed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        MatchingResponseResponse response = matchingService.rejectRequest(receiver.getId(), conn.getId());

        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.REJECTED);
        assertThat(response.getChatRoomId()).isNull();
    }

    @Test
    @DisplayName("requester cannot accept or reject own request")
    void requesterCannotAcceptOrReject() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        assertThatThrownBy(() -> matchingService.acceptRequest(requester.getId(), conn.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> matchingService.rejectRequest(requester.getId(), conn.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("third party cannot accept or reject request")
    void thirdPartyCannotAcceptOrReject() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User third = saveUserWithProfile("u3", Gender.MALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        assertThatThrownBy(() -> matchingService.acceptRequest(third.getId(), conn.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> matchingService.rejectRequest(third.getId(), conn.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("request list excludes private and internal account fields")
    void requestList_excludesPrivateAndInternalFields() {
        User me = saveUserWithProfile("u1", Gender.MALE, 100);
        User requester = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), me.getId()));

        MatchingRequestsResponse response = matchingService.getRequests(me.getId());
        MatchingRequestResponse received = response.getReceived().get(0);

        assertThat(response.getReceivedCount()).isEqualTo(1);
        assertThat(received.getStatus()).isEqualTo(ConnectionStatus.PENDING);

        List<String> fields = Arrays.stream(MatchingRequestResponse.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList();
        assertThat(fields).doesNotContain(
                "name", "email", "provider", "socialId", "tickets", "userStatus",
                "studentNum"
        );
        assertThat(received.getAdmissionYear()).isEqualTo(2024);
        assertThat(received.getOnboardingStatus()).isEqualTo(OnboardingStatus.FULL);
        assertThat(received.isProfileExists()).isTrue();
        assertThat(received.getProfile().getInstagram()).isEqualTo("insta");
    }

    @Test
    @DisplayName("request list skips a pending connection whose other user was permanently deleted")
    void requestList_skipsOrphanedConnection() {
        User me = saveUserWithProfile("u1", Gender.MALE, 100);
        matchingConnectionRepository.save(MatchingConnection.createPending(999_999L, me.getId()));

        MatchingRequestsResponse response = matchingService.getRequests(me.getId());

        assertThat(response.getSentCount()).isZero();
        assertThat(response.getReceivedCount()).isZero();
        assertThat(response.getSent()).isEmpty();
        assertThat(response.getReceived()).isEmpty();
    }

    @Test
    @DisplayName("request list skips a pending connection whose other user is inactive")
    void requestList_skipsInactiveUserConnection() {
        User me = saveUserWithProfile("u1", Gender.MALE, 100);
        User requester = saveUserWithProfile("u2", Gender.FEMALE, 100);
        requester.markDeleted();
        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), me.getId()));

        MatchingRequestsResponse response = matchingService.getRequests(me.getId());

        assertThat(response.getReceivedCount()).isZero();
        assertThat(response.getReceived()).isEmpty();
    }

    @Test
    @DisplayName("recommendation dto excludes private and internal account fields")
    void recommendationDto_excludesPrivateAndInternalFields() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        saveUserWithProfile("u2", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());
        assertThat(response.getCandidates()).hasSize(1);

        List<String> fields = Arrays.stream(MatchingCandidateResponse.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList();
        assertThat(fields).doesNotContain(
                "name", "email", "provider", "socialId", "tickets", "status",
                "studentNum"
        );
        assertThat(response.getCandidates().get(0).getAdmissionYear()).isEqualTo(2024);
        assertThat(response.getCandidates().get(0).getOnboardingStatus()).isEqualTo(OnboardingStatus.FULL);
        assertThat(response.getCandidates().get(0).isProfileExists()).isTrue();
        assertThat(response.getCandidates().get(0).getProfile().getInstagram()).isEqualTo("insta");
    }

    @Test
    @DisplayName("blocked users are excluded from recommendations")
    void recommend_excludesBlockedUsers() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User blockedCandidate = saveUserWithProfile("u2", Gender.FEMALE, 100);
        saveUserWithProfile("u3", Gender.FEMALE, 100);

        userBlockRepository.save(UserBlock.create(requester.getId(), blockedCandidate.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId").doesNotContain(blockedCandidate.getId());
    }

    @Test
    @DisplayName("same gender recommendation only returns same gender candidates")
    void recommendSameGender_returnsOnlySameGenderCandidates() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User sameGender = saveUserWithProfile("u2", Gender.MALE, 100);
        saveUserWithProfile("u3", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommendSameGender(requester.getId());

        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getCandidates()).extracting("userId").containsExactly(sameGender.getId());
    }

    @Test
    @DisplayName("same gender recommendation excludes blocked users")
    void recommendSameGender_excludesBlockedUsers() {
        User requester = saveUserWithProfile("u1", Gender.FEMALE, 100);
        User blockedCandidate = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User visibleCandidate = saveUserWithProfile("u3", Gender.FEMALE, 100);
        saveUserWithProfile("u4", Gender.MALE, 100);

        userBlockRepository.save(UserBlock.create(requester.getId(), blockedCandidate.getId()));

        MatchingRecommendationResponse response = matchingService.recommendSameGender(requester.getId());

        assertThat(response.getCandidates()).extracting("userId")
                .containsExactly(visibleCandidate.getId());
    }

    @Test
    @DisplayName("connect fails when users are in a block relation")
    void connect_failsWhenBlockedRelationExists() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));
        userBlockRepository.save(UserBlock.create(target.getId(), requester.getId()));

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.BLOCKED_USER_INTERACTION);
    }

    @Test
    @DisplayName("matching restricted user cannot receive recommendations")
    void recommend_failsWhenUserIsMatchingRestricted() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        requester.restrictMatching(null, "admin restriction");
        saveUserWithProfile("u2", Gender.FEMALE, 100);

        assertThatThrownBy(() -> matchingService.recommend(requester.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCHING_RESTRICTED);
    }

    private User saveUserWithProfile(String socialId, Gender gender, int tickets) {
        String uniqueSocialId = socialId + "-" + UUID.randomUUID();
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(uniqueSocialId)
                .email(uniqueSocialId + "@example.com")
                .name("test")
                .nickname("nick-" + socialId)
                .deptName("Computer Science")
                .studentNum(20240001)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now(java.time.Clock.systemUTC()))
                .tickets(tickets)
                .build();

        User savedUser = userRepository.save(user);

        UserProfile profile = UserProfile.create(
                savedUser,
                170,
                24,
                "INTJ",
                "NO",
                gender,
                MilitaryStatus.NOT_APPLICABLE,
                "NONE",
                "Seoul",
                "hello",
                "insta"
        );

        userProfileRepository.save(profile);
        return savedUser;
    }

    @Test
    void repeatedAcceptReturnsSameRoomAndCreatesOnlyOneEvent() {
        User sender = saveUserWithProfile("s", Gender.MALE, 10);
        User receiver = saveUserWithProfile("r", Gender.FEMALE, 10);
        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(sender.getId(), receiver.getId()));
        Mockito.when(chatService.createOrGetPersonalRoomForConnection(any(), any(), any(), any()))
                .thenReturn(ChatRoomResponse.builder().id(91L).build());
        var first = matchingService.acceptRequest(receiver.getId(), connection.getId());
        var second = matchingService.acceptRequest(receiver.getId(), connection.getId());
        assertThat(second.getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
        assertThat(second.getChatRoomId()).isEqualTo(first.getChatRoomId());
        Mockito.verify(chatService, Mockito.times(1)).createOrGetPersonalRoomForConnection(any(), any(), any(), any());
        assertThat(notificationEvents.count()).isEqualTo(1);
    }

    @Autowired
    private univ.airconnect.matching.repository.MatchingNotificationEventRepository notificationEvents;

    @Test
    void missingIdempotencyHeaderReturnsStructured400WithoutCharge() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new univ.airconnect.matching.controller.MatchingController(matchingService))
                .setControllerAdvice(new univ.airconnect.global.error.GlobalExceptionHandler())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/matching/recommendations"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void expiredRequestCannotCreateChatEvenBeforeWorkerRuns() {
        User sender = saveUserWithProfile("s", Gender.MALE, 10);
        User receiver = saveUserWithProfile("r", Gender.FEMALE, 10);
        MatchingConnection connection = MatchingConnection.createPending(sender.getId(), receiver.getId());
        org.springframework.test.util.ReflectionTestUtils.setField(connection, "connectedAt",
                LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(7).minusSeconds(1));
        matchingConnectionRepository.saveAndFlush(connection);
        var response = matchingService.acceptRequest(receiver.getId(), connection.getId());
        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(matchingConnectionRepository.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(response.getChatRoomId()).isNull();
        Mockito.verifyNoInteractions(chatService);
        assertThat(notificationEvents.count()).isZero();
    }

    @Test
    void sameRejectionReturnsPreviousResultButOppositeDecisionIsRejected() {
        User sender = saveUserWithProfile("s", Gender.MALE, 10);
        User receiver = saveUserWithProfile("r", Gender.FEMALE, 10);
        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(sender.getId(), receiver.getId()));
        matchingService.rejectRequest(receiver.getId(), connection.getId());
        assertThat(matchingService.rejectRequest(receiver.getId(), connection.getId()).getStatus())
                .isEqualTo(ConnectionStatus.REJECTED);
        assertThatThrownBy(() -> matchingService.acceptRequest(receiver.getId(), connection.getId()))
                .isInstanceOf(MatchingException.class);
        assertThat(notificationEvents.count()).isEqualTo(1);
    }
}
