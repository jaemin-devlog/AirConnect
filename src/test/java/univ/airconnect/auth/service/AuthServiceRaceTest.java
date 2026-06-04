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
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.global.security.jwt.JwtProvider;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
    @Mock private UserService userService;
    @Mock private AnalyticsService analyticsService;
    @Mock private AttemptThrottleService attemptThrottleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SocialAuthClient socialAuthClient;

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
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>(RefreshToken.create(userId, deviceId, "stored-hash"));
        AtomicInteger findCalls = new AtomicInteger();
        CountDownLatch firstSaveDone = new CountDownLatch(1);

        doNothing().when(jwtProvider).validateRefreshToken(rawRefreshToken);
        when(jwtProvider.isRefreshToken(rawRefreshToken)).thenReturn(true);
        when(jwtProvider.getUserId(rawRefreshToken)).thenReturn(userId);
        when(jwtProvider.getDeviceId(rawRefreshToken)).thenReturn(deviceId);
        when(refreshTokenRepository.findById(refreshTokenKey)).thenAnswer(invocation -> {
            int call = findCalls.incrementAndGet();
            if (call > 1) {
                await(firstSaveDone, "refresh save");
            }
            return Optional.ofNullable(storedToken.get());
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            storedToken.set(saved);
            firstSaveDone.countDown();
            return saved;
        });
        when(tokenHashService.matches(rawRefreshToken, "stored-hash")).thenReturn(true);
        when(tokenHashService.matches(rawRefreshToken, "new-refresh-hash")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(userId)).thenReturn("new-access-token");
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
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
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
