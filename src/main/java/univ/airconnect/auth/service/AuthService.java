package univ.airconnect.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
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
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;
import univ.airconnect.verification.service.VerificationService;

import java.util.Map;

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
    private final TokenHashService tokenHashService;
    private final UserService userService;
    private final AnalyticsService analyticsService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72;

    @Transactional
    public LoginResponse socialLogin(SocialLoginRequest request) {
        log.info("Social login started: provider={}", request.getProvider());

        validateSocialLoginRequest(request);

        SocialAuthClient client = socialAuthResolver.getClient(request.getProvider());
        String socialId = client.getSocialId(request.getSocialToken());
        String email = resolveEmail(request);

        User user = userRepository.findByProviderAndSocialId(request.getProvider(), socialId)
                .orElseGet(() -> {
                    log.info("Creating new user for social login: provider={}, socialIdMasked={}",
                            request.getProvider(), maskSocialId(socialId));
                    return userRepository.save(User.create(request.getProvider(), socialId, email));
                });

        validateUserStatus(user);
        user.markActive();

        log.info("Social login completed: userId={}", user.getId());
        return issueLoginResponse(user, request.getDeviceId(), request.getProvider().name());
    }

    @Transactional
    public LoginResponse emailSignUp(EmailSignUpRequest request) {
        validateEmailSignUpRequest(request);

        String verifiedEmail = verificationService.resolveVerifiedEmail(request.getVerificationToken());
        String normalizedEmail = normalizeEmail(verifiedEmail);

        if (userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, normalizedEmail).isPresent()) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User created;
        try {
            created = userRepository.save(
                    User.createEmailUser(normalizedEmail, passwordEncoder.encode(request.getPassword()))
            );
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        verificationService.consumeVerifiedEmail(request.getVerificationToken());
        created.markActive();

        log.info("Email sign-up completed: userId={}, emailMasked={}", created.getId(), maskEmail(normalizedEmail));
        return issueLoginResponse(created, request.getDeviceId(), SocialProvider.EMAIL.name());
    }

    @Transactional
    public LoginResponse emailLogin(EmailLoginRequest request) {
        log.info("Email login payload received: hasEmail={}, hasPassword={}, hasDeviceId={}",
                request != null && request.getEmail() != null && !request.getEmail().isBlank(),
                request != null && request.getPassword() != null && !request.getPassword().isBlank(),
                request != null && request.getDeviceId() != null && !request.getDeviceId().isBlank());

        validateEmailLoginRequest(request);

        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, normalizedEmail)
                .orElseThrow(() -> new AuthException(AuthErrorCode.EMAIL_LOGIN_FAILED));

        validateUserStatus(user);

        String savedPasswordHash = user.getPasswordHash();
        if (savedPasswordHash == null || !passwordEncoder.matches(request.getPassword(), savedPasswordHash)) {
            throw new AuthException(AuthErrorCode.EMAIL_LOGIN_FAILED);
        }

        user.markActive();

        log.info("Email login completed: userId={}, emailMasked={}",
                user.getId(), maskEmail(normalizedEmail));
        return issueLoginResponse(user, request.getDeviceId(), SocialProvider.EMAIL.name());
    }

    @Transactional
    public TokenPairResponse refresh(TokenRefreshRequest request) {
        log.info("Token refresh started: deviceIdMasked={}", maskDeviceId(request.getDeviceId()));

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

        boolean hashMatched = tokenHashService.matches(request.getRefreshToken(), savedToken.getToken());
        boolean legacyPlainMatched = request.getRefreshToken().equals(savedToken.getToken());
        if (!hashMatched && !legacyPlainMatched) {
            refreshTokenRepository.deleteById(refreshTokenKey);
            log.warn("Refresh token reuse detected: userId={}, deviceIdMasked={}",
                    userId, maskDeviceId(request.getDeviceId()));
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (isRestrictedStatus(user.getStatus())) {
            refreshTokenRepository.deleteById(refreshTokenKey);
            log.warn("Refresh token revoked due to blocked user status: userId={}, status={}",
                    userId, user.getStatus());
        }

        validateUserStatus(user);
        user.markActive();

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());
        String newRefreshTokenHash = tokenHashService.hash(newRefreshToken);

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), newRefreshTokenHash)
        );

        log.info("Token refresh completed: userId={}", userId);
        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String deviceId) {
        log.info("Logout requested: userId={}, deviceIdMasked={}", userId, maskDeviceId(deviceId));

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
        if (request.getProvider() == SocialProvider.KAKAO) {
            throw new AuthException(AuthErrorCode.KAKAO_LOGIN_DISABLED);
        }
        if (request.getProvider() == SocialProvider.EMAIL) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.getSocialToken() == null || request.getSocialToken().isBlank()) {
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_REQUIRED);
        }
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }
    }

    private void validateEmailSignUpRequest(EmailSignUpRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.getVerificationToken() == null || request.getVerificationToken().isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        validatePasswordFormat(request.getPassword());
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            throw new AuthException(AuthErrorCode.DEVICE_ID_REQUIRED);
        }
    }

    private void validateEmailLoginRequest(EmailLoginRequest request) {
        if (request == null) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new AuthException(AuthErrorCode.EMAIL_PASSWORD_REQUIRED);
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
        if (user.getStatus() == UserStatus.RESTRICTED) {
            throw new AuthException(AuthErrorCode.USER_RESTRICTED);
        }
    }

    private boolean isRestrictedStatus(UserStatus status) {
        return status == UserStatus.DELETED || status == UserStatus.SUSPENDED || status == UserStatus.RESTRICTED;
    }

    private LoginResponse issueLoginResponse(User user, String deviceId, String providerName) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), deviceId);
        String refreshTokenHash = tokenHashService.hash(refreshToken);

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), deviceId, refreshTokenHash)
        );

        UserMeResponse userInfo = userService.getMe(user.getId());
        analyticsService.trackServerEvent(
                AnalyticsEventType.USER_LOGGED_IN,
                user.getId(),
                Map.of(
                        "provider", providerName,
                        "deviceId", deviceId
                )
        );

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userInfo)
                .build();
    }

    private void validatePasswordFormat(String password) {
        if (password == null || password.isBlank()) {
            throw new AuthException(AuthErrorCode.EMAIL_PASSWORD_REQUIRED);
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_FORMAT);
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars()
                .anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));
        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.isBlank() || normalized.length() > 100) {
            throw new AuthException(AuthErrorCode.INVALID_LOGIN_REQUEST);
        }
        return normalized;
    }

    private String buildRefreshTokenKey(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return "-";
        }
        if (deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }

    private String maskSocialId(String socialId) {
        if (socialId == null || socialId.isBlank()) {
            return "***";
        }
        if (socialId.length() <= 8) {
            return "***";
        }
        return socialId.substring(0, 2) + "***" + socialId.substring(socialId.length() - 2);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
