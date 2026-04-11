package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.service.oauth.apple.AppleAccountRevocationService;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.request.DeleteAccountRequest;
import univ.airconnect.user.dto.request.ChangePasswordRequest;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;
import univ.airconnect.verification.service.VerificationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AnalyticsService analyticsService;
    private final ChatService chatService;
    private final PushDeviceRepository pushDeviceRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppleAccountRevocationService appleAccountRevocationService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;

    private static final String USER_ACTIVITY_TOUCH_KEY_PREFIX = "analytics:user:last-active:";
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    @Value("${app.upload.profile-image-dir:/tmp/airconnect/profile-images}")
    private String profileImageDir;

    // ...existing code...

    @Transactional
    public SignUpResponse signUp(Long userId, SignUpRequest request) {
        log.info("📝 회원가입 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ 사용자를 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        if (request.getName() == null || request.getName().isBlank()) {
            log.warn("⚠️ 이름이 필수입니다");
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        ensureUserActive(user);

        user.completeSignUp(
                request.getName(),
                request.getNickname(),
                request.getStudentNum(),
                request.getDeptName()
        );

        userProfileRepository.findByUserId(userId)
                .ifPresentOrElse(
                        profile -> profile.update(
                                request.getHeight(),
                                request.getAge(),
                                request.getMbti(),
                                request.getSmoking(),
                                request.getGender(),
                                request.getMilitary(),
                                request.getReligion(),
                                request.getResidence(),
                                request.getIntro(),
                                request.getInstagram()
                        ),
                        () -> userProfileRepository.save(UserProfile.create(
                                user,
                                request.getHeight(),
                                request.getAge(),
                                request.getMbti(),
                                request.getSmoking(),
                                request.getGender(),
                                request.getMilitary(),
                                request.getReligion(),
                                request.getResidence(),
                                request.getIntro(),
                                request.getInstagram()
                        ))
                );

        log.info("✅ 회원가입/프로필 생성 완료: userId={}, name={}", userId, request.getName());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deptName", request.getDeptName());
        payload.put("nickname", request.getNickname());
        if (request.getGender() != null) {
            payload.put("gender", request.getGender().name());
        }
        analyticsService.trackServerEvent(AnalyticsEventType.SIGN_UP_COMPLETED, userId, payload);

        return new SignUpResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus().toString(),
                user.getOnboardingStatus().toString(),
                true,
                false
        );
    }

    @Transactional
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        ensureUserActive(user);
        String iosAppAccountToken = user.ensureIosAppAccountToken();

        log.debug("🔗 현재 imageUrlBase: {}", imageUrlBase);
        UserProfileResponse profile = userProfileRepository.findByUserId(userId)
                .map(userProfile -> {
                    log.debug("📸 저장된 profileImagePath (파일명): {}", userProfile.getProfileImagePath());
                    UserProfileResponse resp = UserProfileResponse.from(userProfile, imageUrlBase);
                    log.debug("📸 변환된 profileImagePath (URL): {}", resp.getProfileImagePath());
                    return resp;
                })
                .orElse(null);

        // 마일리스톤 정보 조회
        boolean profileImageUploaded = userMilestoneRepository.existsByUserIdAndMilestoneType(
                userId, MilestoneType.PROFILE_IMAGE_UPLOADED
        );
        boolean emailVerified = userMilestoneRepository.existsByUserIdAndMilestoneType(
                userId, MilestoneType.EMAIL_VERIFIED
        );

        return UserMeResponse.builder()
                .userId(user.getId())
                .provider(user.getProvider())
                .socialId(user.getSocialId())
                .email(user.getEmail())
                .name(user.getName())
                .deptName(user.getDeptName())
                .nickname(user.getNickname())
                .studentNum(user.getStudentNum())
                .age(profile != null ? profile.getAge() : null)
                .status(user.getStatus())
                .onboardingStatus(user.getOnboardingStatus())
                .profileExists(profile != null)
                .profileImageUploaded(profileImageUploaded)
                .emailVerified(emailVerified)
                .tickets(user.getTickets())
                .iosAppAccountToken(iosAppAccountToken)
                .profile(profile)
                .build();
    }

    @Transactional
    public UserProfileResponse createProfile(Long userId, UpdateProfileRequest request) {
        log.info("📸 프로필 생성 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ 사용자를 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        ensureUserActive(user);

        if (userProfileRepository.findByUserId(userId).isPresent()) {
            log.warn("⚠️ 이미 프로필이 존재함: userId={}", userId);
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        UserProfile userProfile = UserProfile.create(
                user,
                request.getHeight(),
                request.getAge(),
                request.getMbti(),
                request.getSmoking(),
                request.getGender(),
                request.getMilitary(),
                request.getReligion(),
                request.getResidence(),
                request.getIntro(),
                request.getInstagram()
        );

        userProfileRepository.save(userProfile);

        log.info("✅ 프로필 생성 완료: userId={}", userId);

        return UserProfileResponse.from(userProfile, imageUrlBase);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("🔄 프로필 업데이트 시작: userId={}", userId);

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("❌ 프로필을 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        ensureUserActive(userProfile.getUser());

        userProfile.updatePartially(
                request.getHeight(),
                request.getAge(),
                request.getMbti(),
                request.getSmoking(),
                request.getGender(),
                request.getMilitary(),
                request.getReligion(),
                request.getResidence(),
                request.getIntro(),
                request.getInstagram()
        );

        log.info("✅ 프로필 업데이트 완료: userId={}", userId);

        return UserProfileResponse.from(userProfile, imageUrlBase);
    }

    public UserProfileResponse getProfile(Long userId) {
        log.info("📖 프로필 조회: userId={}", userId);
        log.debug("🔗 현재 imageUrlBase: {}", imageUrlBase);

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("❌ 프로필을 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        ensureUserActive(userProfile.getUser());

        log.debug("📸 저장된 profileImagePath (파일명): {}", userProfile.getProfileImagePath());
        UserProfileResponse response = UserProfileResponse.from(userProfile, imageUrlBase);
        log.debug("📸 변환된 profileImagePath (URL): {}", response.getProfileImagePath());

        return response;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (request == null) {
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        ensureUserActive(user);

        if (!user.isEmailProvider()) {
            throw new UserException(UserErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }

        validatePasswordFormat(request.getNewPassword());

        String verifiedEmail = verificationService.resolveVerifiedEmail(request.getVerificationToken());
        if (user.getEmail() == null || !user.getEmail().trim().equalsIgnoreCase(verifiedEmail)) {
            throw new VerificationException(VerificationErrorCode.VERIFIED_EMAIL_MISMATCH);
        }

        verificationService.consumeVerifiedEmail(request.getVerificationToken());
        user.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        purgeRefreshTokens(userId);

        log.info("✅ 비밀번호 변경 완료(토큰 무효화 포함): userId={}, emailMasked={}", userId, maskEmail(verifiedEmail));
    }

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        deleteAccount(userId, request, null);
    }

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request, String traceId) {
        boolean reasonProvided = request != null
                && request.getReason() != null
                && !request.getReason().isBlank();
        log.info("🗑️ 회원 탈퇴 처리 시작: userId={}, reasonProvided={}, traceId={}",
                userId, reasonProvided, traceIdOrDash(traceId));

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        revokeAppleTokenOnDelete(user, request, traceId);

        if (user.getStatus() == UserStatus.DELETED) {
            log.info("ℹ️ 이미 탈퇴된 사용자입니다. 후처리만 재시도합니다: userId={}", userId);
        } else {
            user.anonymizeForDeletion();
            user.markDeleted();
        }
        anonymizeProfileAndDeleteImage(userId);

        int refreshTokensRevoked = purgeRefreshTokens(userId);
        int chatSessionsRevoked = purgeChatSessions(userId);
        int pushDevicesRevoked = revokePushDevices(userId);
        purgeUserActivityTouch(userId);

        log.info("✅ 회원 탈퇴 완료: userId={}, status={}, refreshTokensRevoked={}, chatSessionsRevoked={}, pushDevicesRevoked={}",
                userId,
                user.getStatus(),
                refreshTokensRevoked,
                chatSessionsRevoked,
                pushDevicesRevoked);
    }

    private void revokeAppleTokenOnDelete(User user, DeleteAccountRequest request, String traceId) {
        try {
            AppleAccountRevocationService.AppleRevocationResult revokeResult =
                    appleAccountRevocationService.revokeOnAccountDeletion(user, request, traceId);
            if (revokeResult.attempted() && !revokeResult.success()) {
                log.warn("Apple revoke failed but account deletion will continue. traceId={}, userId={}, source={}, reason={}",
                        traceIdOrDash(traceId),
                        user != null ? user.getId() : null,
                        revokeResult.source(),
                        revokeResult.reason());
            }
        } catch (Exception ex) {
            log.warn("Apple revoke process raised unexpected error but account deletion will continue. traceId={}, userId={}, reason={}",
                    traceIdOrDash(traceId),
                    user != null ? user.getId() : null,
                    ex.getMessage());
        }
    }

    private void anonymizeProfileAndDeleteImage(Long userId) {
        userProfileRepository.findByUserId(userId).ifPresent(profile -> {
            String profileImagePath = profile.getProfileImagePath();
            profile.anonymize();
            deleteProfileImageFile(profileImagePath);
            log.debug("👤 프로필 정보를 익명화했습니다: userId={}", userId);
        });
    }

    private int purgeRefreshTokens(Long userId) {
        try {
            Iterable<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
            List<String> tokenIds = new ArrayList<>();
            for (RefreshToken token : tokens) {
                if (token == null || token.getId() == null) {
                    continue;
                }
                tokenIds.add(token.getId());
            }
            if (!tokenIds.isEmpty()) {
                refreshTokenRepository.deleteAllById(tokenIds);
            }
            return tokenIds.size();
        } catch (Exception ex) {
            log.warn("RefreshToken 무효화에 실패했습니다. userId={}, reason={}", userId, ex.getMessage());
            return 0;
        }
    }

    private int purgeChatSessions(Long userId) {
        try {
            return chatService.invalidateSessionsByUserId(userId);
        } catch (Exception ex) {
            log.warn("채팅 세션 무효화에 실패했습니다. userId={}, reason={}", userId, ex.getMessage());
            return 0;
        }
    }

    private int revokePushDevices(Long userId) {
        try {
            List<PushDevice> devices = pushDeviceRepository.findByUserIdAndActiveTrue(userId);
            for (PushDevice device : devices) {
                device.releaseTokenOwnership();
            }
            return devices.size();
        } catch (Exception ex) {
            log.warn("푸시 디바이스 비활성화에 실패했습니다. userId={}, reason={}", userId, ex.getMessage());
            return 0;
        }
    }

    private void purgeUserActivityTouch(Long userId) {
        try {
            redisTemplate.delete(USER_ACTIVITY_TOUCH_KEY_PREFIX + userId);
        } catch (Exception ex) {
            log.warn("활동 터치 키 삭제에 실패했습니다. userId={}, reason={}", userId, ex.getMessage());
        }
    }

    private void deleteProfileImageFile(String profileImagePath) {
        if (profileImagePath == null || profileImagePath.isBlank()) {
            return;
        }

        try {
            Path uploadRoot = Paths.get(profileImageDir).toAbsolutePath().normalize();
            Path target = uploadRoot.resolve(profileImagePath).normalize();
            if (!target.startsWith(uploadRoot)) {
                log.warn("프로필 이미지 삭제가 차단되었습니다. path={}", profileImagePath);
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("프로필 이미지 파일 삭제에 실패했습니다. imagePath={}, reason={}", profileImagePath, ex.getMessage());
        }
    }

    private void ensureUserActive(User user) {
        if (user == null) {
            return;
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UserException(UserErrorCode.USER_DELETED);
        }
    }

    private String traceIdOrDash(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "-";
        }
        return traceId;
    }

    private void validatePasswordFormat(String password) {
        if (password == null || password.isBlank()) {
            throw new UserException(UserErrorCode.PASSWORD_REQUIRED);
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new UserException(UserErrorCode.PASSWORD_INVALID_FORMAT);
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars()
                .anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));
        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new UserException(UserErrorCode.PASSWORD_INVALID_FORMAT);
        }
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
