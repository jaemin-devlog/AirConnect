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

    @Transactional
    public LoginResponse socialLogin(SocialLoginRequest request) {
        log.info("Social login started: provider={}", request.getProvider());

        validateSocialLoginRequest(request);

        SocialAuthClient client = socialAuthResolver.getClient(request.getProvider());
        String socialId = client.getSocialId(request.getSocialToken());
        String email = resolveEmail(request);

        User user = userRepository.findByProviderAndSocialId(request.getProvider(), socialId)
                .orElseGet(() -> {
                    log.info("Creating new user for social login: provider={}, socialId={}",
                            request.getProvider(), socialId);
                    return userRepository.save(User.create(request.getProvider(), socialId, email));
                });

        validateUserStatus(user);

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), refreshToken)
        );

        UserMeResponse userInfo = userService.getMe(user.getId());
        log.info("Social login completed: userId={}", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }

    @Transactional
    public TokenPairResponse refresh(TokenRefreshRequest request) {
        log.info("Token refresh started: deviceId={}", request.getDeviceId());

        validateRefreshRequest(request);
        jwtProvider.validateRefreshToken(request.getRefreshToken());

        if (!jwtProvider.isRefreshToken(request.getRefreshToken())) {
            throw new AuthException(AuthErrorCode.NOT_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(request.getRefreshToken());
        String deviceIdFromToken = jwtProvider.getDeviceId(request.getRefreshToken());

        if (!request.getDeviceId().equals(deviceIdFromToken)) {
            throw new AuthException(AuthErrorCode.DEVICE_MISMATCH);
        }

        String refreshTokenKey = buildRefreshTokenKey(userId, request.getDeviceId());
        RefreshToken savedToken = refreshTokenRepository.findById(refreshTokenKey)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!savedToken.getToken().equals(request.getRefreshToken())) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        validateUserStatus(user);

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), newRefreshToken)
        );

        log.info("Token refresh completed: userId={}", userId);
        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String deviceId) {
        log.info("Logout requested: userId={}, deviceId={}", userId, deviceId);

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_LOGOUT_REQUEST);
        }

        refreshTokenRepository.deleteById(buildRefreshTokenKey(userId, deviceId));
        log.info("Logout completed: userId={}", userId);
    }

    private String resolveEmail(SocialLoginRequest request) {
        if (request.getProvider() != SocialProvider.APPLE) {
            return null;
        }
        return appleAuthClient.getEmail(request.getSocialToken());
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
}
