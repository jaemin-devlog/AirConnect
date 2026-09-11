package univ.airconnect.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.repository.SocialLoginDeviceBindingRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.service.MatchingLifecycleService;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationPreferenceRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.repository.PushEventRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserSchoolConsentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserPurgeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserSchoolConsentRepository userSchoolConsentRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private MatchingLifecycleService matchingLifecycleService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private SocialLoginDeviceBindingRepository socialLoginDeviceBindingRepository;
    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private PushEventRepository pushEventRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private AdminAuditLogService adminAuditLogService;

    private AdminUserPurgeService adminUserPurgeService;

    @BeforeEach
    void setUp() {
        adminUserPurgeService = new AdminUserPurgeService(
                userRepository,
                userProfileRepository,
                userSchoolConsentRepository,
                chatRoomMemberRepository,
                matchingConnectionRepository,
                matchingLifecycleService,
                refreshTokenRepository,
                socialLoginDeviceBindingRepository,
                pushDeviceRepository,
                notificationPreferenceRepository,
                notificationRepository,
                notificationOutboxRepository,
                pushEventRepository,
                userMilestoneRepository,
                adminAuditLogService
        );
    }

    @Test
    void permanentlyDeleteDeletedUser_deletesUserOwnedRowsAndUser() {
        User user = deletedUser(7L);
        RefreshToken refreshToken = RefreshToken.create(7L, "device-1", "hash");

        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(notificationOutboxRepository.deleteByUserId(7L)).thenReturn(2L);
        when(pushEventRepository.deleteByUserId(7L)).thenReturn(3L);
        when(notificationRepository.deleteByUserId(7L)).thenReturn(4L);
        when(notificationPreferenceRepository.deleteByUserId(7L)).thenReturn(1L);
        when(pushDeviceRepository.deleteByUserId(7L)).thenReturn(1L);
        when(socialLoginDeviceBindingRepository.deleteByUserId(7L)).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(7L)).thenReturn(List.of(refreshToken));
        when(userMilestoneRepository.deleteByUserId(7L)).thenReturn(2L);
        when(chatRoomMemberRepository.deleteByUserId(7L)).thenReturn(1L);
        when(userProfileRepository.deleteByUserId(7L)).thenReturn(1L);
        when(userSchoolConsentRepository.deleteByUserId(7L)).thenReturn(1L);

        AdminDtos.UserPermanentDeleteResult result =
                adminUserPurgeService.permanentlyDeleteDeletedUser(999L, 7L);

        assertThat(result.userDeleted()).isTrue();
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.provider()).isEqualTo("APPLE");
        assertThat(result.socialId()).isEqualTo("apple-sub-7");
        assertThat(result.deletedSocialDeviceBindingRows()).isEqualTo(1L);
        assertThat(result.deletedRefreshTokenRows()).isEqualTo(1L);
        assertThat(result.deletedNotificationOutboxRows()).isEqualTo(2L);

        verify(refreshTokenRepository).deleteAllById(List.of(refreshToken.getId()));
        verify(matchingConnectionRepository).deleteByUser1IdOrUser2Id(7L, 7L);
        verify(matchingLifecycleService).deleteUserMatchingArtifacts(7L);
        verify(userRepository).delete(user);
        verify(userRepository).flush();
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void permanentlyDeleteDeletedUser_rejectsActiveUser() {
        User user = activeUser(8L);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserPurgeService.permanentlyDeleteDeletedUser(999L, 8L))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    private User deletedUser(Long id) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("apple-sub-" + id)
                .email(null)
                .status(UserStatus.DELETED)
                .onboardingStatus(OnboardingStatus.BASIC)
                .tickets(0)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User activeUser(Long id) {
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId("kakao-" + id)
                .email("u" + id + "@airconnect.test")
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(10)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
