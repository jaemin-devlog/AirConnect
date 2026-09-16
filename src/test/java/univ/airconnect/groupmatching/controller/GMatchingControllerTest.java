package univ.airconnect.groupmatching.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.dto.response.GMatchingResponse;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingControllerTest {

    @Mock GMatchingService matchingService;
    @Mock GTemporaryTeamMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @InjectMocks GMatchingController controller;

    @Test
    void roomResponse_includesEachMembersActualSchoolVerificationStatus() {
        GTemporaryTeamRoom room = GTemporaryTeamRoom.createInviteOnly(1L, GTeamGender.M, GTeamSize.TWO);
        ReflectionTestUtils.setField(room, "id", 100L);
        GTemporaryTeamMember leader = GTemporaryTeamMember.create(100L, 1L, true);
        GTemporaryTeamMember member = GTemporaryTeamMember.create(100L, 2L, false);
        User verified = user(1L, "인증 사용자");
        verified.updateVerifiedSchoolEmail("verified@school.ac.kr");
        User unverified = user(2L, "미인증 사용자");

        when(memberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(leader, member));
        when(userRepository.findAllByIdWithProfile(List.of(1L, 2L)))
                .thenReturn(List.of(verified, unverified));

        GMatchingResponse.TemporaryTeamRoomResponse response = ReflectionTestUtils.invokeMethod(
                controller, "toRoomResponse", room, 1L
        );

        assertThat(response).isNotNull();
        assertThat(response.members())
                .extracting(GMatchingResponse.TeamMemberSummaryResponse::emailVerified)
                .containsExactly(true, false);
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .provider(SocialProvider.KAKAO)
                .socialId("social-" + id)
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(10)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
