package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.request.DeleteAccountRequest;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

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

        log.info("✅ 회원가입 완료: userId={}, name={}", userId, request.getName());

        return new SignUpResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus().toString()
        );
    }

    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        ensureUserActive(user);

        log.debug("🔗 현재 imageUrlBase: {}", imageUrlBase);
        UserProfileResponse profile = userProfileRepository.findByUserId(userId)
                .map(userProfile -> {
                    log.debug("📸 저장된 profileImagePath (파일명): {}", userProfile.getProfileImagePath());
                    UserProfileResponse resp = UserProfileResponse.from(userProfile, imageUrlBase);
                    log.debug("📸 변환된 profileImagePath (URL): {}", resp.getProfileImagePath());
                    return resp;
                })
                .orElse(null);

        return UserMeResponse.builder()
                .userId(user.getId())
                .provider(user.getProvider())
                .socialId(user.getSocialId())
                .email(user.getEmail())
                .name(user.getName())
                .deptName(user.getDeptName())
                .nickname(user.getNickname())
                .studentNum(user.getStudentNum())
                .status(user.getStatus())
                .onboardingStatus(user.getOnboardingStatus())
                .profileExists(profile != null)
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
                request.getMbti(),
                request.getSmoking(),
                request.getMilitary(),
                request.getGender(),
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

        userProfile.update(
                request.getHeight(),
                request.getMbti(),
                request.getSmoking(),
                request.getMilitary(),
                request.getGender(),
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
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        String reason = request != null ? request.getReason() : null;
        log.info("🗑️ 회원 탈퇴 처리 시작: userId={}, reason={}", userId, reason);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.DELETED) {
            log.info("ℹ️ 이미 탈퇴한 사용자: userId={}", userId);
            purgeRefreshTokens(userId);
            return;
        }

        user.markDeleted();

        userProfileRepository.findByUserId(userId)
                .ifPresent(profile -> {
                    profile.anonymize();
                    log.debug("👤 프로필 정보를 익명화: userId={}", userId);
                });

        purgeRefreshTokens(userId);
        log.info("✅ 회원 탈퇴 완료: userId={}", userId);
    }

    private void purgeRefreshTokens(Long userId) {
        Iterable<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        for (RefreshToken token : tokens) {
            if (token == null) {
                continue;
            }
            refreshTokenRepository.deleteById(token.getId());
            log.debug("🧹 RefreshToken 삭제: key={}", token.getId());
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
}
