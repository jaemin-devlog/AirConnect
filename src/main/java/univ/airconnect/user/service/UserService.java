package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserProfileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

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

        // 회원가입 정보로 사용자 업데이트
        user.completeSignUp(
                request.getName(),
                request.getNickname(),
                request.getStudentNum()
        );

        userRepository.save(user);
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

        return UserMeResponse.builder()
                .userId(user.getId())
                .provider(user.getProvider())
                .socialId(user.getSocialId())
                .status(user.getStatus())
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

        if (request.getNickname() == null || request.getNickname().isBlank()) {
            log.warn("⚠️ 닉네임이 필수입니다");
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .userId(userId)
                .nickname(request.getNickname())
                .gender(request.getGender())
                .department(request.getDepartment())
                .birthYear(request.getBirthYear())
                .height(request.getHeight())
                .mbti(request.getMbti())
                .smoking(request.getSmoking())
                .military(request.getMilitary())
                .religion(request.getReligion())
                .residence(request.getResidence())
                .intro(request.getIntro())
                .contactStyle(request.getContactStyle())
                .profileImageKey(request.getProfileImageKey())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        userProfileRepository.save(userProfile);
        log.info("✅ 프로필 생성 완료: userId={}", userId);

        return UserProfileResponse.from(userProfile);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("🔄 프로필 업데이트 시작: userId={}", userId);

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("❌ 프로필을 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        userProfile.update(
                request.getNickname(),
                request.getGender(),
                request.getDepartment(),
                request.getBirthYear(),
                request.getHeight(),
                request.getMbti(),
                request.getSmoking(),
                request.getMilitary(),
                request.getReligion(),
                request.getResidence(),
                request.getIntro(),
                request.getContactStyle(),
                request.getProfileImageKey()
        );

        userProfileRepository.save(userProfile);
        log.info("✅ 프로필 업데이트 완료: userId={}", userId);

        return UserProfileResponse.from(userProfile);
    }

    public UserProfileResponse getProfile(Long userId) {
        log.info("📖 프로필 조회: userId={}", userId);

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("❌ 프로필을 찾을 수 없음: userId={}", userId);
                    return new UserException(UserErrorCode.USER_NOT_FOUND);
                });

        return UserProfileResponse.from(userProfile);
    }
}

