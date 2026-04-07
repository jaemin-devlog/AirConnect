package univ.airconnect.moderation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.dto.request.CreateUserReportRequest;
import univ.airconnect.moderation.dto.response.SupportInfoResponse;
import univ.airconnect.moderation.dto.response.UserBlockCreateResponse;
import univ.airconnect.moderation.dto.response.UserReportResponse;
import univ.airconnect.moderation.exception.ModerationErrorCode;
import univ.airconnect.moderation.exception.ModerationException;
import univ.airconnect.moderation.infrastructure.ModerationConfig;
import univ.airconnect.moderation.infrastructure.ModerationProperties;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        ModerationConfig.class,
        UserReportService.class,
        UserBlockService.class,
        UserBlockPolicyService.class,
        ModerationSupportService.class
})
class ModerationServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserReportService userReportService;

    @Autowired
    private UserBlockService userBlockService;

    @Autowired
    private ModerationSupportService moderationSupportService;

    @Autowired
    private ModerationProperties moderationProperties;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Test
    @DisplayName("신고 생성 성공")
    void createReport_success() {
        User reporter = saveUser("reporter");
        User reported = saveUser("reported");

        CreateUserReportRequest request = new CreateUserReportRequest();
        ReflectionTestUtils.setField(request, "reportedUserId", reported.getId());
        ReflectionTestUtils.setField(request, "reportReason", ReportReasonCode.HARASSMENT);
        ReflectionTestUtils.setField(request, "detail", "욕설 및 반복 메시지");

        UserReportResponse response = userReportService.createReport(reporter.getId(), request);

        assertThat(response.getReporterUserId()).isEqualTo(reporter.getId());
        assertThat(response.getReportedUserId()).isEqualTo(reported.getId());
        assertThat(response.getReportReason()).isEqualTo(ReportReasonCode.HARASSMENT);
    }

    @Test
    @DisplayName("자기 자신 신고 실패")
    void createReport_failForSelfReport() {
        User me = saveUser("self");

        CreateUserReportRequest request = new CreateUserReportRequest();
        ReflectionTestUtils.setField(request, "reportedUserId", me.getId());
        ReflectionTestUtils.setField(request, "reportReason", ReportReasonCode.SPAM);

        assertThatThrownBy(() -> userReportService.createReport(me.getId(), request))
                .isInstanceOf(ModerationException.class)
                .extracting("errorCode")
                .isEqualTo(ModerationErrorCode.REPORT_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("차단 생성 성공 및 중복 차단 멱등 처리")
    void block_successAndDuplicateHandled() {
        User blocker = saveUser("blocker");
        User blocked = saveUser("blocked");

        UserBlockCreateResponse first = userBlockService.block(blocker.getId(), blocked.getId());
        UserBlockCreateResponse second = userBlockService.block(blocker.getId(), blocked.getId());

        assertThat(first.isAlreadyBlocked()).isFalse();
        assertThat(second.isAlreadyBlocked()).isTrue();
        assertThat(second.getBlockedUserId()).isEqualTo(blocked.getId());
    }

    @Test
    @DisplayName("차단 시 차단자 1:1 채팅방 숨김 처리되고, 차단 해제 시 복구된다")
    void block_hidesPersonalRoomForBlocker_andUnblockRestores() {
        User blocker = saveUser("blocker-hide");
        User blocked = saveUser("blocked-hide");

        ChatRoom room = chatRoomRepository.save(ChatRoom.create("1:1", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, blocker));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, blocked));

        userBlockService.block(blocker.getId(), blocked.getId());

        ChatRoomMember blockerMembership = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(room.getId(), blocker.getId())
                .orElseThrow();
        assertThat(blockerMembership.isHidden()).isTrue();

        userBlockService.unblock(blocker.getId(), blocked.getId());

        ChatRoomMember restoredMembership = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(room.getId(), blocker.getId())
                .orElseThrow();
        assertThat(restoredMembership.isHidden()).isFalse();
    }

    @Test
    @DisplayName("차단된 상대와의 1:1 채팅방은 차단자 목록 조회에서 제외된다")
    void block_excludesHiddenRoomFromBlockerRoomList() {
        User blocker = saveUser("blocker-list");
        User blocked = saveUser("blocked-list");

        ChatRoom room = chatRoomRepository.save(ChatRoom.create("hidden-room", ChatRoomType.PERSONAL));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, blocker));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, blocked));

        userBlockService.block(blocker.getId(), blocked.getId());

        List<ChatRoomMember> visibleRoomsForBlocker = chatRoomMemberRepository.findByUser_IdWithRoom(blocker.getId());
        List<ChatRoomMember> visibleRoomsForBlocked = chatRoomMemberRepository.findByUser_IdWithRoom(blocked.getId());

        assertThat(visibleRoomsForBlocker).isEmpty();
        assertThat(visibleRoomsForBlocked).hasSize(1);
    }

    @Test
    @DisplayName("support 정보 조회 성공")
    void supportInfo_success() {
        moderationProperties.getSupport().setEmail("support@airconnect.app");
        moderationProperties.getSupport().setUrl("https://airconnect.app/support");
        moderationProperties.getSupport().setContactText("운영팀에 문의해 주세요.");

        SupportInfoResponse response = moderationSupportService.getSupportInfo();

        assertThat(response.getSupportEmail()).isEqualTo("support@airconnect.app");
        assertThat(response.getSupportUrl()).isEqualTo("https://airconnect.app/support");
        assertThat(response.getContactText()).isEqualTo("운영팀에 문의해 주세요.");
    }

    private User saveUser(String key) {
        String socialId = key + "-" + UUID.randomUUID();
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId(socialId)
                .email(socialId + "@example.com")
                .nickname("nick-" + key)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(100)
                .build();
        return userRepository.save(user);
    }
}
