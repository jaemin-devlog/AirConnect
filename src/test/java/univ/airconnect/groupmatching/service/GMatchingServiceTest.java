package univ.airconnect.groupmatching.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingServiceTest {

    @Mock
    private GTemporaryTeamRoomRepository temporaryTeamRoomRepository;
    @Mock
    private GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    @Mock
    private GTeamReadyStateRepository teamReadyStateRepository;
    @Mock
    private GMatchResultRepository matchResultRepository;
    @Mock
    private GFinalGroupChatRoomRepository finalGroupChatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ChatService chatService;
    @Mock
    private GMatchingEventPublisher matchingEventPublisher;
    @Mock
    private GMatchingPushService matchingPushService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void createTemporaryTeamRoom_throwsWhenLeaderGenderMismatched() {
        GMatchingService service = createService();
        Long leaderId = 10L;
        User leader = createUser(leaderId, "leader");
        UserProfile profile = createProfile(leader, Gender.FEMALE);

        when(userRepository.findById(leaderId)).thenReturn(Optional.of(leader));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(leaderId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(leaderId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.createTemporaryTeamRoom(
                leaderId,
                "team-a",
                GTeamGender.M,
                GTeamSize.TWO,
                GGenderFilter.ANY,
                GTeamVisibility.PUBLIC,
                null,
                null
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(chatService, never()).createGroupRoomWithMembers(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void joinPublicRoom_throwsWhenJoinerGenderMismatched() {
        GMatchingService service = createService();
        Long roomId = 200L;
        Long userId = 20L;

        User joiner = createUser(userId, "joiner");
        UserProfile profile = createProfile(joiner, Gender.FEMALE);
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(teamRoom.isPublicRoom()).thenReturn(true);
        when(teamRoom.getLeaderId()).thenReturn(999L);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.OPEN);
        when(teamRoom.isFull()).thenReturn(false);
        when(teamRoom.getTeamGender()).thenReturn(GTeamGender.M);
        when(userRepository.findById(userId)).thenReturn(Optional.of(joiner));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(userId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.joinPublicRoom(roomId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(temporaryTeamMemberRepository, never())
                .existsByTeamRoomIdAndUserIdAndLeftAtIsNull(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void canSubscribeTeamRoom_returnsTrueForActiveMember() {
        GMatchingService service = createService();
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(true);

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isTrue();
    }

    @Test
    void canSubscribeTeamRoom_returnsTrueForPastMemberWhenRoomClosed() {
        GMatchingService service = createService();
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);
        GTemporaryTeamRoom room = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(false);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));
        when(temporaryTeamRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(GTemporaryTeamRoomStatus.CLOSED);

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isTrue();
    }

    @Test
    void canSubscribeTeamRoom_returnsFalseWhenNoMembership() {
        GMatchingService service = createService();

        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(false);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isFalse();
    }

    private GMatchingService createService() {
        return new GMatchingService(
                temporaryTeamRoomRepository,
                temporaryTeamMemberRepository,
                teamReadyStateRepository,
                matchResultRepository,
                finalGroupChatRoomRepository,
                chatRoomMemberRepository,
                userRepository,
                userProfileRepository,
                chatService,
                matchingEventPublisher,
                matchingPushService,
                redisTemplate
        );
    }

    private User createUser(Long userId, String nickname) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.dev");
        user.completeSignUp("name-" + userId, nickname, 20230000 + userId.intValue(), "dept");
        return user;
    }

    private UserProfile createProfile(User user, Gender gender) {
        return UserProfile.create(
                user,
                null,
                null,
                null,
                null,
                gender,
                null,
                null,
                null,
                null,
                null
        );
    }
}
