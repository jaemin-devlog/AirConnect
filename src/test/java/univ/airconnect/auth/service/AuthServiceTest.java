package univ.airconnect.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.security.TokenHashService;
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenHashService tokenHashService;
    @Mock
    private UserService userService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private SocialAuthClient socialAuthClient;

    @InjectMocks
    private AuthService authService;

    @Test
    void socialLogin_savesHashedRefreshToken() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.KAKAO, "kakao-social-token", "device-12345678");
        User user = createUser(11L);

        when(socialAuthResolver.getClient(SocialProvider.KAKAO)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("kakao-social-token")).thenReturn("kakao-social-id");
        when(userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "kakao-social-id"))
                .thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(11L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(11L, "device-12345678")).thenReturn("refresh-token-raw");
        when(tokenHashService.hash("refresh-token-raw")).thenReturn("refresh-token-hash");
        when(userService.getMe(11L)).thenReturn(UserMeResponse.builder().userId(11L).build());

        LoginResponse response = authService.socialLogin(request);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-raw");
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("refresh-token-hash");
        assertThat(refreshTokenCaptor.getValue().getToken()).isNotEqualTo("refresh-token-raw");
    }

    @Test
    void refresh_reuseDetected_deletesStoredTokenAndBlocksRequest() {
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

        verify(refreshTokenRepository).deleteById(refreshTokenKey);
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
        when(jwtProvider.createAccessToken(userId)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(userId, request.getDeviceId())).thenReturn("new-refresh-token");
        when(tokenHashService.hash("new-refresh-token")).thenReturn("new-refresh-token-hash");

        TokenPairResponse response = authService.refresh(request);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("new-refresh-token-hash");
        verify(refreshTokenRepository, never()).deleteById(eq(refreshTokenKey));
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

    private User createUser(Long userId) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@airconnect.test");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
