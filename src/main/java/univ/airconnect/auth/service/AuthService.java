package univ.airconnect.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.service.oauth.SocialAuthClient;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SocialAuthResolver socialAuthResolver;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenPairResponse socialLogin(SocialLoginRequest request) {
        validateSocialLoginRequest(request);

        SocialAuthClient client = socialAuthResolver.getClient(request.provider());
        String socialId = client.getSocialId(request.socialToken());

        User user = userRepository.findByProviderAndSocialId(request.provider(), socialId)
                .orElseGet(() -> userRepository.save(User.create(request.provider(), socialId)));

        validateUserStatus(user);

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), request.deviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.deviceId(), refreshToken)
        );

        return new TokenPairResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenPairResponse refresh(TokenRefreshRequest request) {
        validateRefreshRequest(request);

        jwtProvider.validateRefreshToken(request.refreshToken());

        if (!jwtProvider.isRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.NOT_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(request.refreshToken());
        String deviceIdFromToken = jwtProvider.getDeviceId(request.refreshToken());

        if (!request.deviceId().equals(deviceIdFromToken)) {
            throw new AuthException(AuthErrorCode.DEVICE_MISMATCH);
        }

        String refreshTokenKey = buildRefreshTokenKey(userId, request.deviceId());

        RefreshToken savedToken = refreshTokenRepository.findById(refreshTokenKey)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!savedToken.getToken().equals(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        validateUserStatus(user);

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), request.deviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.deviceId(), newRefreshToken)
        );

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_LOGOUT_REQUEST);
        }

        refreshTokenRepository.deleteById(buildRefreshTokenKey(userId, deviceId));
    }

    private void validateSocialLoginRequest(SocialLoginRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.provider() == null) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_REQUIRED);
        }
        if (request.socialToken() == null || request.socialToken().isBlank()) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_REQUIRED);
        }
        if (request.deviceId() == null || request.deviceId().isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }
    }

    private void validateRefreshRequest(TokenRefreshRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_REQUEST);
        }
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REQUIRED);
        }
        if (request.deviceId() == null || request.deviceId().isBlank()) {
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