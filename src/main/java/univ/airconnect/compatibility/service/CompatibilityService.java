package univ.airconnect.compatibility.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.compatibility.domain.CompatibilityProfile;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.dto.response.CompatibilityResponse;
import univ.airconnect.compatibility.exception.CompatibilityErrorCode;
import univ.airconnect.compatibility.exception.CompatibilityException;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompatibilityService {

    private final UserRepository userRepository;
    private final CompatibilityScoreCalculator compatibilityScoreCalculator;
    private final CompatibilitySummaryService compatibilitySummaryService;

    public CompatibilityResponse getCompatibility(Long myUserId, Long targetUserId) {
        if (myUserId == null || targetUserId == null || myUserId.equals(targetUserId)) {
            throw new CompatibilityException(CompatibilityErrorCode.INVALID_TARGET);
        }

        Map<Long, User> users = fetchUsersWithProfile(List.of(myUserId, targetUserId));
        User me = requireUser(users, myUserId);
        User target = requireUser(users, targetUserId);

        validateUsable(me);
        validateUsable(target);

        CompatibilityProfile myProfile = CompatibilityProfile.from(me);
        CompatibilityProfile targetProfile = CompatibilityProfile.from(target);
        validateRequiredProfile(myProfile);
        validateRequiredProfile(targetProfile);

        CompatibilityResult result = compatibilityScoreCalculator.calculate(myProfile, targetProfile);
        String summary = compatibilitySummaryService.summarize(result);

        return CompatibilityResponse.from(result, summary);
    }

    private Map<Long, User> fetchUsersWithProfile(Collection<Long> userIds) {
        return userRepository.findAllByIdWithProfile(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private User requireUser(Map<Long, User> users, Long userId) {
        User user = users.get(userId);
        if (user == null) {
            throw new CompatibilityException(CompatibilityErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void validateUsable(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CompatibilityException(CompatibilityErrorCode.USER_INACTIVE);
        }
        if (user.getUserProfile() == null) {
            throw new CompatibilityException(CompatibilityErrorCode.PROFILE_REQUIRED);
        }
    }

    private void validateRequiredProfile(CompatibilityProfile profile) {
        if (profile.age() == null
                || isBlank(profile.deptName())
                || profile.studentNum() == null
                || profile.height() == null
                || isBlank(profile.mbti())
                || isBlank(profile.smoking())
                || isBlank(profile.religion())
                || isBlank(profile.residence())) {
            throw new CompatibilityException(CompatibilityErrorCode.PROFILE_INCOMPLETE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
