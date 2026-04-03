package univ.airconnect.groupmatching.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.dto.response.GMatchingResponse;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingControllerTest {

    @Mock
    private GMatchingService matchingService;
    @Mock
    private ChatService chatService;
    @Mock
    private GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    @Mock
    private GTeamReadyStateRepository teamReadyStateRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void toRoomResponse_includesMemberProfileImage() {
        GMatchingController controller = new GMatchingController(
                matchingService,
                chatService,
                temporaryTeamMemberRepository,
                teamReadyStateRepository,
                userRepository
        );

        Long roomId = 101L;
        Long userId = 10L;
        LocalDateTime now = LocalDateTime.now();

        GTemporaryTeamRoom room = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);
        GTeamReadyState readyState = mock(GTeamReadyState.class);
        User user = createUser(userId, "leader", "profiles/u10.png");

        when(room.getId()).thenReturn(roomId);
        when(room.getLeaderId()).thenReturn(userId);
        when(room.getTeamName()).thenReturn("test-team");
        when(room.getTeamGender()).thenReturn(GTeamGender.M);
        when(room.getTeamSize()).thenReturn(GTeamSize.TWO);
        when(room.getCurrentMemberCount()).thenReturn(1);
        when(room.isFull()).thenReturn(false);
        when(room.getStatus()).thenReturn(GTemporaryTeamRoomStatus.OPEN);
        when(room.getOpponentGenderFilter()).thenReturn(GGenderFilter.ANY);
        when(room.getVisibility()).thenReturn(GTeamVisibility.PUBLIC);
        when(room.getTempChatRoomId()).thenReturn(501L);
        when(room.getInviteCode()).thenReturn("123456");
        when(room.getQueueToken()).thenReturn(null);
        when(room.getQueuedAt()).thenReturn(null);
        when(room.getMatchedAt()).thenReturn(null);
        when(room.getClosedAt()).thenReturn(null);
        when(room.getCancelledAt()).thenReturn(null);
        when(room.getCreatedAt()).thenReturn(now);
        when(room.getUpdatedAt()).thenReturn(now);

        when(member.getUserId()).thenReturn(userId);
        when(member.isLeader()).thenReturn(true);
        when(member.isActiveMember()).thenReturn(true);
        when(member.getJoinedAt()).thenReturn(now);
        when(member.getLeftAt()).thenReturn(null);

        when(readyState.getUserId()).thenReturn(userId);
        when(readyState.isReady()).thenReturn(true);

        when(temporaryTeamMemberRepository.findByTeamRoomIdOrderByJoinedAtAsc(roomId)).thenReturn(List.of(member));
        when(teamReadyStateRepository.findByTeamRoomIdOrderByIdAsc(roomId)).thenReturn(List.of(readyState));
        when(userRepository.findAllByIdWithProfile(List.of(userId))).thenReturn(List.of(user));

        GMatchingResponse.TemporaryTeamRoomResponse response =
                ReflectionTestUtils.invokeMethod(controller, "toRoomResponse", room, userId);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).nickname()).isEqualTo("leader");
        assertThat(response.members().get(0).profileImage()).isEqualTo("profiles/u10.png");
    }

    private User createUser(Long userId, String nickname, String profileImagePath) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.dev");
        user.completeSignUp("name-" + userId, nickname, 20230000 + userId.intValue(), "dept");
        ReflectionTestUtils.setField(user, "id", userId);

        UserProfile profile = UserProfile.create(
                user,
                null,
                null,
                null,
                null,
                Gender.MALE,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(profile, "userId", userId);
        ReflectionTestUtils.setField(profile, "profileImagePath", profileImagePath);
        ReflectionTestUtils.setField(user, "userProfile", profile);
        return user;
    }
}
