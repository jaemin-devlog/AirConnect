package univ.airconnect.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.dto.request.EmailLoginRequest;
import univ.airconnect.auth.dto.request.EmailSignUpRequest;
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
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;
import univ.airconnect.verification.domain.entity.VerifiedSchoolEmail;
import univ.airconnect.verification.repository.VerifiedSchoolEmailRepository;
import univ.airconnect.verification.service.VerificationService;

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
    @Mock
    private VerificationService verificationService;
    @Mock
    private VerifiedSchoolEmailRepository verifiedSchoolEmailRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AttemptThrottleService attemptThrottleService;

    @InjectMocks
    private AuthService authService;

    @Test
    void socialLogin_savesHashedRefreshToken() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.APPLE, "apple-identity-token", "device-12345678");
        User user = createUser(11L);

        when(socialAuthResolver.getClient(SocialProvider.APPLE)).thenReturn(socialAuthClient);
        when(socialAuthClient.getSocialId("apple-identity-token")).thenReturn("apple-social-id");
        when(appleAuthClient.getEmail("apple-identity-token")).thenReturn("u11@airconnect.test");
        when(userRepository.findByProviderAndSocialId(SocialProvider.APPLE, "apple-social-id"))
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

    @Test
    void socialLogin_kakaoDisabled_throws() {
        SocialLoginRequest request = new SocialLoginRequest(SocialProvider.KAKAO, "kakao-token", "device-1");

        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.KAKAO_LOGIN_DISABLED);
    }

    @Test
    void emailSignUp_requiresVerificationToken() {
        EmailSignUpRequest request = new EmailSignUpRequest(null, "Passw0rd1", "device-1");

        assertThatThrownBy(() -> authService.emailSignUp(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_LOGIN_REQUEST);
    }

    @Test
    void emailSignUp_requiresSpecialCharacterInPassword() {
        EmailSignUpRequest request = new EmailSignUpRequest("verify-token", "Passw0rd1", "device-1");

        assertThatThrownBy(() -> authService.emailSignUp(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_PASSWORD_FORMAT);
    }

    @Test
    void emailSignUp_failsWhenVerifiedSchoolEmailAlreadyLinkedToAppleAccount() {
        EmailSignUpRequest request = new EmailSignUpRequest("verify-token", "Passw0rd!@", "device-1");

        when(verificationService.resolveVerifiedEmail("verify-token")).thenReturn("student@office.hanseo.ac.kr");
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(Optional.empty());
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "student@office.hanseo.ac.kr"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(false);
        when(userRepository.existsByVerifiedSchoolEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(true);

        assertThatThrownBy(() -> authService.emailSignUp(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void emailLogin_succeedsWithEmailAndPasswordOnly() {
        EmailLoginRequest request = new EmailLoginRequest("user@office.hanseo.ac.kr", "Passw0rd!@", "device-1");
        User user = User.createEmailUser("user@office.hanseo.ac.kr", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 77L);

        when(attemptThrottleService.isLocked("email_login", "user@office.hanseo.ac.kr", "unknown")).thenReturn(false);
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "user@office.hanseo.ac.kr"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!@", "encoded-password")).thenReturn(true);
        when(jwtProvider.createAccessToken(77L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(77L, "device-1")).thenReturn("refresh-token-raw");
        when(tokenHashService.hash("refresh-token-raw")).thenReturn("refresh-token-hash");
        when(userService.getMe(77L)).thenReturn(UserMeResponse.builder().userId(77L).build());

        LoginResponse response = authService.emailLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(attemptThrottleService).clear("email_login", "user@office.hanseo.ac.kr", "unknown");
        verify(verificationService, never()).resolveVerifiedEmail(any());
        verify(verificationService, never()).consumeVerifiedEmail(any());
    }

    @Test
    void emailLogin_failsWhenPasswordMismatch() {
        EmailLoginRequest request = new EmailLoginRequest("user@office.hanseo.ac.kr", "wrong-password", "device-1");
        User user = User.createEmailUser("user@office.hanseo.ac.kr", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 77L);

        when(attemptThrottleService.isLocked("email_login", "user@office.hanseo.ac.kr", "unknown")).thenReturn(false);
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "user@office.hanseo.ac.kr"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);
        when(attemptThrottleService.recordFailure("email_login", "user@office.hanseo.ac.kr", "unknown", 5, 900L, 900L))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.emailLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_LOGIN_FAILED);
    }

    @Test
    void emailLogin_failsWhenTooManyAttempts() {
        EmailLoginRequest request = new EmailLoginRequest("user@office.hanseo.ac.kr", "wrong-password", "device-1");

        when(attemptThrottleService.isLocked("email_login", "user@office.hanseo.ac.kr", "203.0.113.9")).thenReturn(true);

        assertThatThrownBy(() -> authService.emailLogin(request, "203.0.113.9"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_LOGIN_TEMPORARILY_LOCKED);
    }

    @Test
    void emailLogin_requiresEmail() {
        EmailLoginRequest request = new EmailLoginRequest(null, "Passw0rd!@", "device-1");

        assertThatThrownBy(() -> authService.emailLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_LOGIN_REQUEST);
    }

    @Test
    void emailSignUp_allowsUnlinkedVerifiedSchoolEmailReservationAndClaimsIt() {
        EmailSignUpRequest request = new EmailSignUpRequest("verify-token", "Passw0rd!@", "device-1");
        VerifiedSchoolEmail reservation = VerifiedSchoolEmail.reserve("student@office.hanseo.ac.kr", null);

        when(verificationService.resolveVerifiedEmail("verify-token")).thenReturn("student@office.hanseo.ac.kr");
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase("student@office.hanseo.ac.kr"))
                .thenReturn(Optional.of(reservation));
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "student@office.hanseo.ac.kr"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(false);
        when(userRepository.existsByVerifiedSchoolEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!@")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 88L);
            return saved;
        });
        when(jwtProvider.createAccessToken(88L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(88L, "device-1")).thenReturn("refresh-token");
        when(tokenHashService.hash("refresh-token")).thenReturn("refresh-hash");
        when(userService.getMe(88L)).thenReturn(UserMeResponse.builder().userId(88L).build());

        LoginResponse response = authService.emailSignUp(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(verificationService).claimVerifiedEmailForUser("student@office.hanseo.ac.kr", 88L);
        verify(verificationService).consumeVerifiedEmail("verify-token");
    }

    @Test
    void emailSignUp_failsWhenVerifiedSchoolEmailReservationAlreadyLinkedToAnotherUser() {
        EmailSignUpRequest request = new EmailSignUpRequest("verify-token", "Passw0rd!@", "device-1");
        VerifiedSchoolEmail reservation = VerifiedSchoolEmail.reserve("student@office.hanseo.ac.kr", 5L);

        when(verificationService.resolveVerifiedEmail("verify-token")).thenReturn("student@office.hanseo.ac.kr");
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase("student@office.hanseo.ac.kr"))
                .thenReturn(Optional.of(reservation));
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "student@office.hanseo.ac.kr"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(false);
        when(userRepository.existsByVerifiedSchoolEmailIgnoreCase("student@office.hanseo.ac.kr")).thenReturn(false);

        assertThatThrownBy(() -> authService.emailSignUp(request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    private User createUser(Long userId) {
        User user = User.create(SocialProvider.APPLE, "social-" + userId, "u" + userId + "@airconnect.test");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
