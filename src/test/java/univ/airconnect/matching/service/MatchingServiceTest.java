package univ.airconnect.matching.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.domain.entity.MatchingQueueEntry;
import univ.airconnect.matching.dto.response.MatchingConnectResponse;
import univ.airconnect.matching.dto.response.MatchingRecommendationResponse;
import univ.airconnect.matching.dto.response.MatchingRequestsResponse;
import univ.airconnect.matching.dto.response.MatchingResponseResponse;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingQueueEntryRepository;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
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
    private MatchingQueueEntryRepository matchingQueueEntryRepository;

    @Autowired
    private MatchingExposureRepository matchingExposureRepository;

    @Autowired
    private MatchingConnectionRepository matchingConnectionRepository;

    @MockBean
    private ChatService chatService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("매칭 시작 시 큐에 활성 엔트리가 생성된다")
    void start_createsQueueEntry() {
        Long userId = transactionTemplate.execute(status ->
                saveUserWithProfile("user-1", Gender.MALE, 100).getId()
        );

        matchingService.start(userId);

        Optional<MatchingQueueEntry> entry = matchingQueueEntryRepository.findByUserIdAndActiveTrue(userId);
        assertThat(entry).isPresent();
    }

    @Test
    @DisplayName("추천 요청 시 티켓이 차감되고 후보가 반환된다")
    void recommend_consumesTicketAndReturnsCandidate() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User candidate = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        matchingQueueEntryRepository.save(MatchingQueueEntry.create(requester.getId()));
        matchingQueueEntryRepository.save(MatchingQueueEntry.create(candidate.getId()));

        MatchingRecommendationResponse response = matchingService.recommend(requester.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(99);
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(matchingExposureRepository.existsByUserIdAndCandidateUserId(requester.getId(), candidate.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("추천 요청은 매칭 시작 전이면 실패한다")
    void recommend_requiresQueueStarted() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);

        assertThatThrownBy(() -> matchingService.recommend(requester.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCHING_NOT_STARTED);
    }

    @Test
    @DisplayName("컨택 요청 시 PENDING 연결이 생성되고 티켓 2장이 소모된다")
    void connect_createsPendingAndConsumesTickets() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));

        MatchingConnectResponse response = matchingService.connect(requester.getId(), target.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getTickets()).isEqualTo(98);
        assertThat(response.isAlreadyConnected()).isFalse();
        assertThat(response.getChatRoomId()).isNull();

        Long user1 = Math.min(requester.getId(), target.getId());
        Long user2 = Math.max(requester.getId(), target.getId());
        MatchingConnection connection = matchingConnectionRepository.findByUser1IdAndUser2Id(user1, user2).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.PENDING);
        assertThat(connection.getChatRoomId()).isNull();
    }

    @Test
    @DisplayName("컨택 요청은 노출되지 않은 후보에게 보낼 수 없다")
    void connect_requiresExposure() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.CANDIDATE_NOT_EXPOSED);
    }

    @Test
    @DisplayName("컨택 요청은 티켓이 부족하면 실패한다")
    void connect_requiresTickets() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 1);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        matchingExposureRepository.save(MatchingExposure.create(requester.getId(), target.getId()));

        assertThatThrownBy(() -> matchingService.connect(requester.getId(), target.getId()))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INSUFFICIENT_TICKETS);
    }

    @Test
    @DisplayName("요청 목록은 보낸/받은 리스트로 분리되어 반환된다")
    void getRequests_returnsSentAndReceived() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), target.getId())
        );

        MatchingRequestsResponse requesterView = matchingService.getRequests(requester.getId());
        assertThat(requesterView.getSentCount()).isEqualTo(1);
        assertThat(requesterView.getReceivedCount()).isEqualTo(0);
        assertThat(requesterView.getSent()).hasSize(1);

        MatchingRequestsResponse targetView = matchingService.getRequests(target.getId());
        assertThat(targetView.getSentCount()).isEqualTo(0);
        assertThat(targetView.getReceivedCount()).isEqualTo(1);
        assertThat(targetView.getReceived()).hasSize(1);
        assertThat(targetView.getReceived().get(0).getConnectionId()).isEqualTo(connection.getId());
    }

    @Test
    @DisplayName("요청 수락 시 채팅방이 생성되고 상태가 ACCEPTED로 변경된다")
    void acceptRequest_marksAccepted() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), target.getId())
        );

        Mockito.when(chatService.createChatRoom(any(), any(), any(), any()))
                .thenReturn(ChatRoomResponse.builder()
                        .id(99L)
                        .name("소개팅 1:1")
                        .type(ChatRoomType.PERSONAL)
                        .createdAt(LocalDateTime.now())
                        .build());

        MatchingResponseResponse response = matchingService.acceptRequest(target.getId(), connection.getId());

        MatchingConnection reloaded = matchingConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
        assertThat(reloaded.getChatRoomId()).isEqualTo(99L);
        assertThat(reloaded.getRespondedAt()).isNotNull();
        assertThat(response.getChatRoomId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("요청 거절 시 상태가 REJECTED로 변경된다")
    void rejectRequest_marksRejected() {
        User requester = saveUserWithProfile("user-1", Gender.MALE, 100);
        User target = saveUserWithProfile("user-2", Gender.FEMALE, 100);

        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(requester.getId(), target.getId())
        );

        MatchingResponseResponse response = matchingService.rejectRequest(target.getId(), connection.getId());

        MatchingConnection reloaded = matchingConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ConnectionStatus.REJECTED);
        assertThat(reloaded.getRespondedAt()).isNotNull();
        assertThat(response.getChatRoomId()).isNull();
    }

    private User saveUserWithProfile(String socialId, Gender gender, int tickets) {
        String uniqueSocialId = socialId + "-" + UUID.randomUUID();
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(uniqueSocialId)
                .email(uniqueSocialId + "@example.com")
                .name("테스트")
                .nickname("닉네임")
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
