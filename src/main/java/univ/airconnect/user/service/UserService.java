package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

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