package univ.airconnect.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.domain.entity.SocialLoginDeviceBinding;
import univ.airconnect.auth.dto.request.EmailLoginRequest;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.repository.SocialLoginDeviceBindingRepository;
import univ.airconnect.auth.security.TokenHashService;
import univ.airconnect.auth.security.RefreshTokenRotationService;
import univ.airconnect.auth.security.AccessTokenRevocationService;
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.notification.service.PushDeviceService;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SocialAuthResolver socialAuthResolver;
    @Mock
    private AppleAuthClient appleAuthClient;
    @Mock
    private AdminAccountService adminAccountService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private SocialLoginDeviceBindingRepository socialLoginDeviceBindingRepository;
    @Mock
    private TokenHashService tokenHashService;
    @Mock
    private RefreshTokenRotationService refreshTokenRotationService;
    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;
    @Mock
    private UserService userService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private AttemptThrottleService attemptThrottleService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SocialAuthClient socialAuthClient;
    @Mock
    private PushDeviceService pushDeviceService;
    @Mock
    private ChatService chatService;

    @InjectMocks
    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(socialLoginDeviceBindingRepository.findByDeviceId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void logout_revokesRefreshTokenAndDeactivatesCurrentUsersDevice() {
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(300);
        when(jwtProvider.getAccessTokenExpiresAt("logout-access-token")).thenReturn(expiresAt);
        authService.logout(41L, "device-logout", "logout-access-token");

        verify(accessTokenRevocationService).revoke("logout-access-token", expiresAt);
        verify(refreshTokenRepository).deleteById("41:device-logout");
        verify(pushDeviceService).deactivateIfPresent(41L, "device-logout");
        verify(chatService).invalidateSessionsByUserId(41L);
    }

    @Test
    void socialLogin_savesHashedRefreshToken() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.APPLE, "apple-identity-token", "device-12345678");
        User user = createUser(11L);

        when(socialAuthResolver.getClient(SocialProvider.APPLE)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("apple-identity-token")).thenReturn("apple-social-id");
        when(appleAuthClient.getEmail("apple-identity-token")).thenReturn("u11@airconnect.test");
        when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, "apple-social-id"))
                .thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(eq(11L), eq("device-12345678"), anyString())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(11L, "device-12345678")).thenReturn("refresh-token-raw");
        when(tokenHashService.hash("refresh-token-raw")).thenReturn("refresh-token-hash");
        when(userService.getMe(11L)).thenReturn(UserMeResponse.builder().userId(11L).build());

        LoginResponse response = authService.socialLogin(request);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-raw");
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("refresh-token-hash");
        assertThat(refreshTokenCaptor.getValue().getToken()).isNotEqualTo("refresh-token-raw");
        assertThat(refreshTokenCaptor.getValue().getSessionId()).isNotBlank();
        verify(jwtProvider).createAccessToken(11L, "device-12345678", refreshTokenCaptor.getValue().getSessionId());
    }

    @Test
    void socialLogin_allowsKakaoWhenClientIsAvailable() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.KAKAO, "kakao-access-token", "device-kakao");

        when(socialAuthResolver.getClient(SocialProvider.KAKAO)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("kakao-access-token")).thenReturn("kakao-social-id");
        when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "kakao-social-id"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 51L);
            return created;
        });
        when(jwtProvider.createAccessToken(eq(51L), eq("device-kakao"), anyString())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(51L, "device-kakao")).thenReturn("refresh-token");
        when(tokenHashService.hash("refresh-token")).thenReturn("refresh-hash");
        when(userService.getMe(51L)).thenReturn(UserMeResponse.builder().userId(51L).build());

        LoginResponse response = authService.socialLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(User.class));
        verify(socialLoginDeviceBindingRepository).save(any(SocialLoginDeviceBinding.class));
    }

    @Test
    void socialLogin_rejectsWhenDeviceAlreadyBoundToAnotherUser() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.APPLE, "apple-identity-token", "device-locked");
        User user = createUser(11L);
        SocialLoginDeviceBinding binding = SocialLoginDeviceBinding.bind(99L, "device-locked");

        when(socialAuthResolver.getClient(SocialProvider.APPLE)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("apple-identity-token")).thenReturn("apple-social-id");
        when(appleAuthClient.getEmail("apple-identity-token")).thenReturn("u11@airconnect.test");
        when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, "apple-social-id"))
                .thenReturn(Optional.of(user));
        when(socialLoginDeviceBindingRepository.findByDeviceId("device-locked"))
                .thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.DEVICE_ALREADY_LINKED_TO_ANOTHER_ACCOUNT);

        verify(refreshTokenRepository, never()).save(any());
        verify(userService, never()).getMe(any());
    }

    @Test
    void socialLogin_rejectsNewSocialAccountCreationWhenDeviceAlreadyBound() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.KAKAO, "kakao-access-token", "device-locked");
        SocialLoginDeviceBinding binding = SocialLoginDeviceBinding.bind(77L, "device-locked");

        when(socialAuthResolver.getClient(SocialProvider.KAKAO)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("kakao-access-token")).thenReturn("kakao-social-id");
        when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "kakao-social-id"))
                .thenReturn(Optional.empty());
        when(socialLoginDeviceBindingRepository.findByDeviceId("device-locked"))
                .thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.DEVICE_ALREADY_LINKED_TO_ANOTHER_ACCOUNT);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void socialLogin_rejectsEmailProviderRequests() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.EMAIL, "ignored", "device-1");

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_LOGIN_REQUEST);
    }

    @Test
    void adminLogin_returnsRefreshableSession_andRevokesExistingRefreshTokens() {
        EmailLoginRequest request = new EmailLoginRequest("Admin@AirConnect.test", "super-secret", "device-admin");
        User adminUser = createAdminUser(88L, "admin@airconnect.test", "stored-hash");
        RefreshToken legacyToken = RefreshToken.create(88L, "legacy-device", "legacy-hash");

        when(adminAccountService.isEnabledAndConfigured()).thenReturn(true);
        when(adminAccountService.loginAttemptIdentifier()).thenReturn("admin@airconnect.test");
        when(adminAccountService.normalizedEmail()).thenReturn("admin@airconnect.test");
        when(attemptThrottleService.isLocked("admin_login", "admin@airconnect.test", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "admin@airconnect.test"))
                .thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("super-secret", "stored-hash")).thenReturn(true);
        when(jwtProvider.createAccessToken(eq(88L), eq("device-admin"), anyString())).thenReturn("admin-access-token");
        when(jwtProvider.createRefreshToken(88L, "device-admin")).thenReturn("admin-refresh-token");
        when(tokenHashService.hash("admin-refresh-token")).thenReturn("admin-refresh-token-hash");
        when(refreshTokenRepository.findByUserId(88L)).thenReturn(List.of(legacyToken));
        when(userService.getMe(88L)).thenReturn(UserMeResponse.builder().userId(88L).role(UserRole.ADMIN).build());

        LoginResponse response = authService.adminLogin(request, "127.0.0.1");

        assertThat(response.getAccessToken()).isEqualTo("admin-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("admin-refresh-token");
        verify(attemptThrottleService).clear("admin_login", "admin@airconnect.test", "127.0.0.1");
        verify(refreshTokenRepository).deleteAllById(List.of(legacyToken.getId()));
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("admin-refresh-token-hash");
    }

    @Test
    void adminLogin_recordsFailureForWrongPassword() {
        EmailLoginRequest request = new EmailLoginRequest("admin@airconnect.test", "wrong-password", "device-admin");
        User adminUser = createAdminUser(89L, "admin@airconnect.test", "stored-hash");

        when(adminAccountService.isEnabledAndConfigured()).thenReturn(true);
        when(adminAccountService.loginAttemptIdentifier()).thenReturn("admin@airconnect.test");
        when(adminAccountService.normalizedEmail()).thenReturn("admin@airconnect.test");
        when(attemptThrottleService.isLocked("admin_login", "admin@airconnect.test", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "admin@airconnect.test"))
                .thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);
        when(attemptThrottleService.recordFailure("admin_login", "admin@airconnect.test", "127.0.0.1",
                5, 300L, 900L)).thenReturn(false);

        assertThatThrownBy(() -> authService.adminLogin(request, "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_LOGIN_FAILED);
    }

    @Test
    void adminLogin_blocksLockedRequest() {
        EmailLoginRequest request = new EmailLoginRequest("admin@airconnect.test", "secret", "device-admin");

        when(adminAccountService.isEnabledAndConfigured()).thenReturn(true);
        when(adminAccountService.loginAttemptIdentifier()).thenReturn("admin@airconnect.test");
        when(attemptThrottleService.isLocked("admin_login", "admin@airconnect.test", "127.0.0.1")).thenReturn(true);

        assertThatThrownBy(() -> authService.adminLogin(request, "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    void adminLogin_blocksAccountBudgetEvenWithDifferentClientIp() {
        when(adminAccountService.isEnabledAndConfigured()).thenReturn(true);
        when(adminAccountService.loginAttemptIdentifier()).thenReturn("admin@airconnect.test");
        when(attemptThrottleService.isLocked("admin_login", "admin@airconnect.test", "198.51.100.7"))
                .thenReturn(false);
        when(attemptThrottleService.isLocked("admin_login_account", "admin@airconnect.test", "account"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.adminLogin(
                new EmailLoginRequest("admin@airconnect.test", "wrong", "device"), "198.51.100.7"))
                .isInstanceOf(AuthException.class).extracting("errorCode")
                .isEqualTo(AuthErrorCode.EMAIL_LOGIN_TEMPORARILY_LOCKED);
        verify(userRepository, never()).findByProviderAndSocialId(any(), any());
    }

    @Test
    void socialLogin_rejectsDeviceIdentifierTooLongForStorageBeforeCallingProvider() {
        assertThatThrownBy(() -> authService.socialLogin(
                new SocialLoginRequest(SocialProvider.APPLE, "dummy", "a".repeat(121))))
                .isInstanceOf(AuthException.class).extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_LOGIN_REQUEST);
        verify(socialAuthResolver, never()).getClient(any());
    }

    @Test
    void refresh_reuseDetected_preservesNewerSessionAndBlocksRequest() {
        TokenRefreshRequest request = new TokenRefreshRequest("refresh-token-raw", "device-12345678");
        Long userId = 15L;
        String refreshTokenKey = userId + ":" + request.getDeviceId();

        doNothing().when(jwtProvider).validateRefreshToken(request.getRefreshToken());
        when(jwtProvider.isRefreshToken(request.getRefreshToken())).thenReturn(true);
        when(jwtProvider.getUserId(request.getRefreshToken())).thenReturn(userId);
        when(jwtProvider.getDeviceId(request.getRefreshToken())).thenReturn(request.getDeviceId());
        when(refreshTokenRepository.findById(refreshTokenKey))
                .thenReturn(Optional.of(RefreshToken.create(userId, request.getDeviceId(), "stored-hash")));
        when(tokenHashService.matches(request.getRefreshToken(), "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

        verify(refreshTokenRepository, never()).deleteById(refreshTokenKey);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_allowsLegacyPlainTokenOnce_andRotatesToHashedToken() {
        TokenRefreshRequest request = new TokenRefreshRequest("legacy-refresh-token", "device-87654321");
        Long userId = 21L;
        String refreshTokenKey = userId + ":" + request.getDeviceId();
        User user = createUser(userId);

        doNothing().when(jwtProvider).validateRefreshToken(request.getRefreshToken());
        when(jwtProvider.isRefreshToken(request.getRefreshToken())).thenReturn(true);
        when(jwtProvider.getUserId(request.getRefreshToken())).thenReturn(userId);
        when(jwtProvider.getDeviceId(request.getRefreshToken())).thenReturn(request.getDeviceId());
        when(refreshTokenRepository.findById(refreshTokenKey))
                .thenReturn(Optional.of(RefreshToken.create(userId, request.getDeviceId(), "legacy-refresh-token")));
        when(tokenHashService.matches(request.getRefreshToken(), "legacy-refresh-token")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(eq(userId), eq(request.getDeviceId()), anyString())).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(userId, request.getDeviceId())).thenReturn("new-refresh-token");
        when(tokenHashService.hash("new-refresh-token")).thenReturn("new-refresh-token-hash");
        when(refreshTokenRotationService.rotate(eq(refreshTokenKey), eq("legacy-refresh-token"), eq("new-refresh-token-hash"), anyString()))
                .thenReturn(true);

        TokenPairResponse response = authService.refresh(request);

        ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRotationService).rotate(eq(refreshTokenKey), eq("legacy-refresh-token"), eq("new-refresh-token-hash"), sessionId.capture());
        assertThat(sessionId.getValue()).isNotBlank();
        verify(jwtProvider).createAccessToken(userId, request.getDeviceId(), sessionId.getValue());
        verify(refreshTokenRepository, never()).save(any());

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository, never()).deleteById(refreshTokenKey);
    }

    @Test
    void refresh_blockedUser_revokesStoredTokenAndFails() {
        TokenRefreshRequest request = new TokenRefreshRequest("refresh-token-raw", "device-12345678");
        Long userId = 30L;
        String refreshTokenKey = userId + ":" + request.getDeviceId();
        User suspendedUser = createUser(userId);
        ReflectionTestUtils.setField(suspendedUser, "status", UserStatus.SUSPENDED);

        doNothing().when(jwtProvider).validateRefreshToken(request.getRefreshToken());
        when(jwtProvider.isRefreshToken(request.getRefreshToken())).thenReturn(true);
        when(jwtProvider.getUserId(request.getRefreshToken())).thenReturn(userId);
        when(jwtProvider.getDeviceId(request.getRefreshToken())).thenReturn(request.getDeviceId());
        when(refreshTokenRepository.findById(refreshTokenKey))
                .thenReturn(Optional.of(RefreshToken.create(userId, request.getDeviceId(), "stored-hash")));
        when(tokenHashService.matches(request.getRefreshToken(), "stored-hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.USER_SUSPENDED);

        verify(refreshTokenRepository).deleteById(refreshTokenKey);
    }

    @Test
    void refreshPreservesLoginSessionAcrossRotation() {
        TokenRefreshRequest request = new TokenRefreshRequest("refresh-token", "device-a");
        when(jwtProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtProvider.getUserId("refresh-token")).thenReturn(15L);
        when(jwtProvider.getDeviceId("refresh-token")).thenReturn("device-a");
        when(refreshTokenRepository.findById("15:device-a"))
                .thenReturn(Optional.of(RefreshToken.create(15L, "device-a", "stored-hash", "stable-session")));
        when(tokenHashService.matches("refresh-token", "stored-hash")).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(createUser(15L)));
        when(jwtProvider.createAccessToken(15L, "device-a", "stable-session")).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(15L, "device-a")).thenReturn("next-refresh");
        when(tokenHashService.hash("next-refresh")).thenReturn("next-hash");
        when(refreshTokenRotationService.rotate("15:device-a", "stored-hash", "next-hash", "stable-session"))
                .thenReturn(true);

        TokenPairResponse result = authService.refresh(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRotationService).rotate("15:device-a", "stored-hash", "next-hash", "stable-session");
        verify(refreshTokenRepository, never()).save(any());
    }

    private User createUser(Long userId) {
        User user = User.create(SocialProvider.APPLE, "social-" + userId, "u" + userId + "@airconnect.test");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private User createAdminUser(Long userId, String email, String passwordHash) {
        User user = User.createEmailUser(email, passwordHash);
        user.changeRole(UserRole.ADMIN);
        user.completeSignUp("Admin", "admin", 99999999, "운영팀");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
