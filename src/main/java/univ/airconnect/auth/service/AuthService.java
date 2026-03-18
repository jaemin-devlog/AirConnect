package univ.airconnect.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.service.MatchingService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SocialAuthResolver socialAuthResolver;
    private final AppleAuthClient appleAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final MatchingService matchingService;

    @Transactional
    public LoginResponse socialLogin(SocialLoginRequest request) {
        log.info("🔐 소셜 로그인 시작: provider={}", request.getProvider());

        validateSocialLoginRequest(request);

        SocialAuthClient client = socialAuthResolver.getClient(request.getProvider());
        String socialId = client.getSocialId(request.getSocialToken());
        log.debug("✅ Social ID 획득: {}", socialId);

        // Apple 로그인인 경우 email 정보 추출
        final String email;
        if (request.getProvider() == SocialProvider.APPLE) {
            email = appleAuthClient.getEmail(request.getSocialToken());
            log.debug("✅ Apple 이메일 획득: {}", email);
        } else {
            email = null;
        }

        User user = userRepository.findByProviderAndSocialId(request.getProvider(), socialId)
                .orElseGet(() -> {
                    log.info("👤 신규 사용자 생성: provider={}, socialId={}",
                            request.getProvider(), socialId);
                    return userRepository.save(User.create(request.getProvider(), socialId, email));
                });

        log.info("✅ 사용자 조회/생성 완료: userId={}", user.getId());

        validateUserStatus(user);

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());

        log.debug("🎫 토큰 생성 완료: userId={}, deviceId={}", user.getId(), request.getDeviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), refreshToken)
        );

        log.info("💾 RefreshToken 저장 완료: userId={}", user.getId());

        tryStartMatchingQueue(user.getId());

        // 사용자 정보 조회 (프로필 포함)
        UserMeResponse userInfo = userService.getMe(user.getId());
        log.info("✅ 사용자 정보 조회 완료: userId={}", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }

    @Transactional
    public TokenPairResponse refresh(TokenRefreshRequest request) {
        log.info("🔄 토큰 갱신 시작: deviceId={}", request.getDeviceId());

        validateRefreshRequest(request);

        jwtProvider.validateRefreshToken(request.getRefreshToken());

        if (!jwtProvider.isRefreshToken(request.getRefreshToken())) {
            log.warn("⚠️ RefreshToken이 아님");
            throw new AuthException(AuthErrorCode.NOT_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(request.getRefreshToken());
        String deviceIdFromToken = jwtProvider.getDeviceId(request.getRefreshToken());

        log.debug("📍 토큰에서 정보 추출: userId={}, deviceId={}", userId, deviceIdFromToken);

        if (!request.getDeviceId().equals(deviceIdFromToken)) {
            log.warn("⚠️ DeviceId 불일치: 요청={}, 토큰={}", request.getDeviceId(), deviceIdFromToken);
            throw new AuthException(AuthErrorCode.DEVICE_MISMATCH);
        }

        String refreshTokenKey = buildRefreshTokenKey(userId, request.getDeviceId());

        RefreshToken savedToken = refreshTokenRepository.findById(refreshTokenKey)
                .orElseThrow(() -> {
                    log.error("❌ RefreshToken 찾을 수 없음: key={}", refreshTokenKey);
                    return new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
                });

        if (!savedToken.getToken().equals(request.getRefreshToken())) {
            log.error("❌ RefreshToken 불일치");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ 사용자 찾을 수 없음: userId={}", userId);
                    return new AuthException(AuthErrorCode.USER_NOT_FOUND);
                });

        validateUserStatus(user);

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), newRefreshToken)
        );

        log.info("✅ 토큰 갱신 완료: userId={}", userId);

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String deviceId) {
        log.info("🚪 로그아웃: userId={}, deviceId={}", userId, deviceId);

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            log.warn("⚠️ 로그아웃 요청 유효하지 않음");
            throw new AuthException(AuthErrorCode.INVALID_LOGOUT_REQUEST);
        }

        refreshTokenRepository.deleteById(buildRefreshTokenKey(userId, deviceId));
        log.info("✅ RefreshToken 삭제 완료: userId={}", userId);
    }

    /**
     * 개발/테스트 환경에서만 사용 가능한 임시 토큰 생성
     * 프로덕션 환경에서는 이 메서드를 호출하지 않습니다.
     */
    @Transactional
    public LoginResponse createTestToken(String deviceId) {
        log.warn("🧪 테스트 토큰 생성 (개발 환경에서만 사용)");

        if (deviceId == null || deviceId.isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }

        // 테스트용 사용자 생성 또는 기존 사용자 사용
        String testSocialId = "test-user-" + System.currentTimeMillis();
        User testUser = userRepository.findByProviderAndSocialId(SocialProvider.KAKAO, testSocialId)
                .orElseGet(() -> {
                    log.info("👤 테스트 사용자 생성: socialId={}", testSocialId);
                    return userRepository.save(User.create(SocialProvider.KAKAO, testSocialId, "test@example.com"));
                });

        log.info("✅ 테스트 사용자 조회/생성 완료: userId={}", testUser.getId());

        validateUserStatus(testUser);

        String accessToken = jwtProvider.createAccessToken(testUser.getId());
        String refreshToken = jwtProvider.createRefreshToken(testUser.getId(), deviceId);

        log.debug("🎫 테스트 토큰 생성 완료: userId={}, deviceId={}", testUser.getId(), deviceId);

        refreshTokenRepository.save(
                RefreshToken.create(testUser.getId(), deviceId, refreshToken)
        );

        log.info("💾 테스트 RefreshToken 저장 완료: userId={}", testUser.getId());

        tryStartMatchingQueue(testUser.getId());

        // 사용자 정보 조회 (프로필 포함)
        UserMeResponse userInfo = userService.getMe(testUser.getId());
        log.info("✅ 테스트 사용자 정보 조회 완료: userId={}", testUser.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }

    private void validateSocialLoginRequest(SocialLoginRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.getProvider() == null) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_REQUIRED);
        }
        if (request.getSocialToken() == null || request.getSocialToken().isBlank()) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_REQUIRED);
        }
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }
    }

    private void validateRefreshRequest(TokenRefreshRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_REQUEST);
        }
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REQUIRED);
        }
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }
    }

    private void validateUserStatus(User user) {
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.USER_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.USER_SUSPENDED);
        }
    }

    private String buildRefreshTokenKey(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    private void tryStartMatchingQueue(Long userId) {
        try {
            matchingService.start(userId);
            log.info("✅ 로그인 직후 매칭 큐 자동 진입 완료: userId={}", userId);
        } catch (MatchingException e) {
            log.info("ℹ️ 로그인 직후 매칭 큐 자동 진입 스킵: userId={}, reason={}", userId, e.getMessage());
        } catch (Exception e) {
            log.error("⚠️ 로그인 직후 매칭 큐 자동 진입 실패(로그인 유지): userId={}", userId, e);
        }
    }
}