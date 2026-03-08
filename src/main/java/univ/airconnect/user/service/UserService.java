package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

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
                request.getPhoneNumber()
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
}