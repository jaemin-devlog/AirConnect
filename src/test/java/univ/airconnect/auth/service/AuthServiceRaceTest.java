package univ.airconnect.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialLoginDeviceBinding;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.repository.SocialLoginDeviceBindingRepository;
import univ.airconnect.auth.security.TokenHashService;
import univ.airconnect.auth.security.RefreshTokenRotationService;
import univ.airconnect.auth.security.AccessTokenRevocationService;
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.notification.service.PushDeviceService;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceRaceTest {

    @Mock private SocialAuthResolver socialAuthResolver;
    @Mock private AppleAuthClient appleAuthClient;
    @Mock private AdminAccountService adminAccountService;
    @Mock private UserRepository userRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SocialLoginDeviceBindingRepository socialLoginDeviceBindingRepository;
    @Mock private TokenHashService tokenHashService;
    @Mock private RefreshTokenRotationService refreshTokenRotationService;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Mock private UserService userService;
    @Mock private AnalyticsService analyticsService;
    @Mock private AttemptThrottleService attemptThrottleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SocialAuthClient socialAuthClient;
    @Mock private PushDeviceService pushDeviceService;
    @Mock private ChatService chatService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(socialLoginDeviceBindingRepository.findByDeviceId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("JWT refresh를 동시에 2번 요청하면 1번만 성공하고 나머지는 reuse로 실패한다")
    void refresh_concurrentRequests_oneSucceedsOneFails() throws Exception {
        Long userId = 15L;
        String deviceId = "device-race-15";
        String refreshTokenKey = userId + ":" + deviceId;
        String rawRefreshToken = "refresh-token-raw";

        User user = createUser(userId);
        AtomicReference<String> storedToken = new AtomicReference<>("stored-hash");
        CountDownLatch bothReadsFinished = new CountDownLatch(2);

        doNothing().when(jwtProvider).validateRefreshToken(rawRefreshToken);
        when(jwtProvider.isRefreshToken(rawRefreshToken)).thenReturn(true);
        when(jwtProvider.getUserId(rawRefreshToken)).thenReturn(userId);
        when(jwtProvider.getDeviceId(rawRefreshToken)).thenReturn(deviceId);
        when(refreshTokenRepository.findById(refreshTokenKey)).thenAnswer(invocation -> {
            String token = storedToken.get();
            bothReadsFinished.countDown();
            await(bothReadsFinished, "both old-token reads");
            return Optional.of(RefreshToken.create(userId, deviceId, token));
        });
        when(refreshTokenRotationService.rotate(eq(refreshTokenKey), eq("stored-hash"), eq("new-refresh-hash"), anyString()))
                .thenAnswer(invocation -> storedToken.compareAndSet("stored-hash", "new-refresh-hash"));
        when(tokenHashService.matches(rawRefreshToken, "stored-hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(eq(userId), eq(deviceId), anyString())).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(userId, deviceId)).thenReturn("new-refresh-token");
        when(tokenHashService.hash("new-refresh-token")).thenReturn("new-refresh-hash");

        TokenRefreshRequest request = new TokenRefreshRequest(rawRefreshToken, deviceId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(task(startGate, () -> authService.refresh(request))),
                    executor.submit(task(startGate, () -> authService.refresh(request)))
            );

            startGate.countDown();

            int successCount = 0;
            int reuseFailureCount = 0;
            for (Future<Object> future : futures) {
                try {
                    Object result = future.get(5, TimeUnit.SECONDS);
                    assertThat(result).isInstanceOf(TokenPairResponse.class);
                    successCount++;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    assertThat(cause).isInstanceOf(AuthException.class);
                    assertThat(((AuthException) cause).getErrorCode())
                            .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
                    reuseFailureCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(reuseFailureCount).isEqualTo(1);
            assertThat(storedToken.get()).isEqualTo("new-refresh-hash");
            verify(refreshTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never()).deleteById(anyString());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void refreshCannotRecreateSessionDeletedByLogoutAfterRead() {
        TokenRefreshRequest request = new TokenRefreshRequest("old-refresh", "device-race");
        when(jwtProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtProvider.getUserId("old-refresh")).thenReturn(15L);
        when(jwtProvider.getDeviceId("old-refresh")).thenReturn("device-race");
        when(refreshTokenRepository.findById("15:device-race"))
                .thenReturn(Optional.of(RefreshToken.create(15L, "device-race", "old-hash")));
        when(tokenHashService.matches("old-refresh", "old-hash")).thenReturn(true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(createUser(15L)));
        when(jwtProvider.createAccessToken(eq(15L), eq("device-race"), anyString())).thenReturn("next-access");
        when(jwtProvider.createRefreshToken(15L, "device-race")).thenReturn("next-refresh");
        when(tokenHashService.hash("next-refresh")).thenReturn("next-hash");
        // Redis key is gone after logout, so atomic compare cannot succeed.
        when(refreshTokenRotationService.rotate(eq("15:device-race"), eq("old-hash"), eq("next-hash"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteById(anyString());
    }

    private Callable<Object> task(CountDownLatch startGate, Callable<Object> delegate) {
        return () -> {
            await(startGate, "start gate");
            return delegate.call();
        };
    }

    private void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for " + label);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + label, e);
        }
    }

    private User createUser(Long userId) {
        User user = User.create(SocialProvider.APPLE, "social-" + userId, "u" + userId + "@airconnect.test");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
