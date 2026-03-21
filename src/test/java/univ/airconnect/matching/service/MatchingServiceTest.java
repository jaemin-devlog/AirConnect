package univ.airconnect.matching.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
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
import univ.airconnect.matching.dto.response.MatchingRequestResponse;
import univ.airconnect.matching.dto.response.MatchingRecommendationResponse;
import univ.airconnect.matching.dto.response.MatchingRequestsResponse;
import univ.airconnect.matching.dto.response.MatchingResponseResponse;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@DataJpaTest
@ActiveProfiles("test")
@Import(MatchingService.class)
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

    @MockitoBean
    private ChatService chatService;

    @Test
    @DisplayName("추천 후보가 2명 있으면 2명 반환되고 티켓이 1 차감된다")
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
    @DisplayName("추천 후보가 1명만 있으면 1명 반환되고 티켓이 1 차감된다")
    void recommend_returnsOneAndConsumesOneTicket() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getCandidates()).extracting("userId").containsExactly(c1.getId());
        assertThat(reloaded.getTickets()).isEqualTo(99);
    }

    @Test
    @DisplayName("추천 후보가 없으면 티켓이 차감되지 않는다")
    void recommend_noCandidate_noTicketConsumed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(response.getCount()).isEqualTo(0);
        assertThat(response.getCandidates()).isEmpty();
        assertThat(reloaded.getTickets()).isEqualTo(100);
    }

    @Test
    @DisplayName("추천 예외가 나면 티켓이 차감되지 않는다")
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
    @DisplayName("같은 추천 사이클 내에서는 중복 노출이 없다")
    void recommend_noDuplicatesWithinCycle() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User c1 = saveUserWithProfile("u2", Gender.FEMALE, 100);
        User c2 = saveUserWithProfile("u3", Gender.FEMALE, 100);
        User c3 = saveUserWithProfile("u4", Gender.FEMALE, 100);

        MatchingRecommendationResponse first = matchingService.recommend(requester.getId());
        MatchingRecommendationResponse second = matchingService.recommend(requester.getId());

        List<Long> allShown = new ArrayList<>(first.getCandidates().stream().map(c -> c.getUserId()).toList());
        allShown.addAll(second.getCandidates().stream().map(c -> c.getUserId()).toList());

        assertThat(allShown).doesNotHaveDuplicates();
        assertThat(allShown).contains(c1.getId(), c2.getId(), c3.getId());
    }

    @Test
    @DisplayName("한 사이클 소진 후에는 노출 이력이 리셋되어 재순환된다")
    void recommend_resetsAfterCycle() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        saveUserWithProfile("u2", Gender.FEMALE, 100);
        saveUserWithProfile("u3", Gender.FEMALE, 100);

        matchingService.recommend(requester.getId());
        matchingService.recommend(requester.getId());

        MatchingRecommendationResponse third = matchingService.recommend(requester.getId());

        assertThat(third.getCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("내가 보낸 PENDING 상대는 추천에서 제외된다")
    void recommend_excludesMyPending() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User target = saveUserWithProfile("u2", Gender.FEMALE, 100);

        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), target.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId").doesNotContain(target.getId());
    }

    @Test
    @DisplayName("상대가 보낸 PENDING 상대도 추천에서 제외된다")
    void recommend_excludesIncomingPending() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User other = saveUserWithProfile("u2", Gender.FEMALE, 100);

        matchingConnectionRepository.save(MatchingConnection.createPending(other.getId(), requester.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        assertThat(response.getCandidates()).extracting("userId").doesNotContain(other.getId());
    }

    @Test
    @DisplayName("활성 채팅 상대는 추천에서 제외되고, 채팅 종료 상대와 거절 상대는 다시 추천된다")
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
    @DisplayName("요청 생성 성공 시 티켓이 2 차감된다")
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
    @DisplayName("PENDING 중복 요청은 티켓이 차감되지 않는다")
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
    @DisplayName("이미 활성 연결이면 요청은 alreadyConnected로 처리되고 티켓이 차감되지 않는다")
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
    @DisplayName("노출되지 않은 상대 요청은 실패하고 티켓이 차감되지 않는다")
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
    @DisplayName("실제 수신자는 요청을 수락할 수 있다")
    void accept_receiverAllowed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        Mockito.when(chatService.createChatRoom(any(), any(), any(), any()))
                .thenReturn(ChatRoomResponse.builder()
                        .id(99L)
                        .name("소개팅 1:1")
                        .type(ChatRoomType.PERSONAL)
                        .createdAt(LocalDateTime.now())
                        .build());

        MatchingResponseResponse response = matchingService.acceptRequest(receiver.getId(), conn.getId());

        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
        assertThat(response.getChatRoomId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("실제 수신자는 요청을 거절할 수 있다")
    void reject_receiverAllowed() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        User receiver = saveUserWithProfile("u2", Gender.FEMALE, 100);
        MatchingConnection conn = matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), receiver.getId()));

        MatchingResponseResponse response = matchingService.rejectRequest(receiver.getId(), conn.getId());

        assertThat(response.getStatus()).isEqualTo(ConnectionStatus.REJECTED);
        assertThat(response.getChatRoomId()).isNull();
    }

    @Test
    @DisplayName("요청자는 자신의 요청을 수락/거절할 수 없다")
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
    @DisplayName("제3자는 수락/거절할 수 없다")
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
    @DisplayName("요청 목록 응답은 name/email/provider 없이 나머지 사용자 정보를 포함한다")
    void requestList_excludesOnlyNameEmailProvider() {
        User me = saveUserWithProfile("u1", Gender.MALE, 100);
        User requester = saveUserWithProfile("u2", Gender.FEMALE, 100);
        matchingConnectionRepository.save(MatchingConnection.createPending(requester.getId(), me.getId()));

        MatchingRequestsResponse response = matchingService.getRequests(me.getId());
        MatchingRequestResponse received = response.getReceived().get(0);

        assertThat(response.getReceivedCount()).isEqualTo(1);
        assertThat(received.getSocialId()).isNotBlank();
        assertThat(received.getOnboardingStatus()).isNotNull();
        assertThat(received.getStatus()).isEqualTo(ConnectionStatus.PENDING);

        List<String> fields = Arrays.stream(MatchingRequestResponse.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList();
        assertThat(fields).doesNotContain("name", "email", "provider");
    }

    @Test
    @DisplayName("추천 응답은 name/email/provider를 제외한 사용자 정보를 포함한다")
    void recommendationDto_excludesOnlyNameEmailProvider() {
        User requester = saveUserWithProfile("u1", Gender.MALE, 100);
        saveUserWithProfile("u2", Gender.FEMALE, 100);

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());
        MatchingCandidateResponse candidate = response.getCandidates().get(0);

        assertThat(candidate.getSocialId()).isNotBlank();
        assertThat(candidate.getTickets()).isNotNull();
        assertThat(candidate.getOnboardingStatus()).isNotNull();

        List<String> fields = Arrays.stream(MatchingCandidateResponse.class.getDeclaredFields())
                .map(field -> field.getName())
                .toList();
        assertThat(fields).doesNotContain("name", "email", "provider");
    }

    private User saveUserWithProfile(String socialId, Gender gender, int tickets) {
        String uniqueSocialId = socialId + "-" + UUID.randomUUID();
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(uniqueSocialId)
                .email(uniqueSocialId + "@example.com")
                .name("테스트")
                .nickname("닉네임-" + socialId)
                .deptName("컴퓨터공학과")
                .studentNum(20240001)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build();

        User savedUser = userRepository.save(user);

        UserProfile profile = UserProfile.create(
                savedUser,
                170,
                "INTJ",
                "NO",
                gender,
                MilitaryStatus.NOT_APPLICABLE,
                "NONE",
                "서울",
                "안녕하세요",
                "insta"
        );

        userProfileRepository.save(profile);
        return savedUser;
    }
}
