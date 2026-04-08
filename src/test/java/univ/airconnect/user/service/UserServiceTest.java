package univ.airconnect.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.service.oauth.apple.AppleAccountRevocationService;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.request.ChangePasswordRequest;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.service.VerificationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ChatService chatService;
    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private AppleAccountRevocationService appleAccountRevocationService;
    @Mock
    private VerificationService verificationService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void deleteAccount_marksUserDeleted_andRevokesSessions() {
        UserService service = createService();
        Long userId = 10L;

        User user = User.create(SocialProvider.KAKAO, "social-10", "u10@test.dev");
        user.completeSignUp("name", "nick", 20230010, "dept");
        ReflectionTestUtils.setField(user, "id", userId);

        UserProfile profile = UserProfile.create(
                user,
                180,
                25,
                "INTJ",
                "N",
                null,
                null,
                "none",
                "seoul",
                "hello",
                "insta"
        );
        ReflectionTestUtils.setField(profile, "userId", userId);
        profile.updateProfileImagePath("profile_10.png");

        RefreshToken tokenA = RefreshToken.create(userId, "device-a", "refresh-a");
        RefreshToken tokenB = RefreshToken.create(userId, "device-b", "refresh-b");

        PushDevice pushDevice = PushDevice.register(
                userId,
                "ios-device",
                PushPlatform.IOS,
                PushProvider.FCM,
                "push-token",
                "apns-token",
                true,
                "1.0.0",
                "17.0",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        );

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(List.of(tokenA, tokenB));
        when(chatService.invalidateSessionsByUserId(userId)).thenReturn(3);
        when(pushDeviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(pushDevice));
        when(appleAccountRevocationService.revokeOnAccountDeletion(user, null, null))
                .thenReturn(AppleAccountRevocationService.AppleRevocationResult.skipped("NON_APPLE_USER"));

        service.deleteAccount(userId, null);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getName()).isNull();
        assertThat(user.getEmail()).isNull();

        assertThat(profile.getIntro()).isNull();
        assertThat(profile.getInstagram()).isNull();
        assertThat(profile.getProfileImagePath()).isNull();

        ArgumentCaptor<Iterable<String>> tokenIdsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(refreshTokenRepository).deleteAllById(tokenIdsCaptor.capture());
        assertThat(tokenIdsCaptor.getValue()).containsExactly(tokenA.getId(), tokenB.getId());

        verify(chatService).invalidateSessionsByUserId(userId);
        verify(redisTemplate).delete("analytics:user:last-active:" + userId);
        verify(pushDeviceRepository).findByUserIdAndActiveTrue(userId);

        assertThat(pushDevice.getActive()).isFalse();
        assertThat(pushDevice.getPushToken()).startsWith("released:");
    }

    @Test
    void deleteAccount_continuesWhenAppleRevokeFails() {
        UserService service = createService();
        Long userId = 20L;

        User appleUser = User.create(SocialProvider.APPLE, "apple-social-20", "apple20@test.dev");
        appleUser.completeSignUp("apple", "appleNick", 20230020, "dept");
        ReflectionTestUtils.setField(appleUser, "id", userId);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(appleUser));
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(List.of());
        when(chatService.invalidateSessionsByUserId(userId)).thenReturn(0);
        when(pushDeviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());
        when(appleAccountRevocationService.revokeOnAccountDeletion(appleUser, null, null))
                .thenReturn(AppleAccountRevocationService.AppleRevocationResult.failed("APPLE_REFRESH_TOKEN", "network-error"));

        service.deleteAccount(userId, null);

        assertThat(appleUser.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(appleUser.getDeletedAt()).isNotNull();
        verify(appleAccountRevocationService).revokeOnAccountDeletion(appleUser, null, null);
    }

    @Test
    void changePassword_onlyAllowsEmailProvider() {
        UserService service = createService();
        Long userId = 99L;
        User appleUser = User.create(SocialProvider.APPLE, "apple-99", "apple99@test.dev");
        ReflectionTestUtils.setField(appleUser, "id", userId);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(appleUser));

        ChangePasswordRequest request = new ChangePasswordRequest();
        ReflectionTestUtils.setField(request, "verificationToken", "verified-token");
        ReflectionTestUtils.setField(request, "newPassword", "Passw0rd123");

        assertThatThrownBy(() -> service.changePassword(userId, request))
                .isInstanceOf(UserException.class);
    }

    private UserService createService() {
        UserService service = new UserService(
                userRepository,
                userProfileRepository,
                userMilestoneRepository,
                refreshTokenRepository,
                analyticsService,
                chatService,
                pushDeviceRepository,
                redisTemplate,
                appleAccountRevocationService,
                verificationService,
                passwordEncoder
        );
        ReflectionTestUtils.setField(service, "imageUrlBase", "http://localhost:8080/api/v1/users/profile-images");
        ReflectionTestUtils.setField(service, "profileImageDir", "/tmp/airconnect-test-profile-images");
        return service;
    }
}
