package univ.airconnect.compatibility.domain;

import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;

public record CompatibilityProfile(
        Long userId,
        Integer age,
        String deptName,
        Integer studentNum,
        Integer height,
        String mbti,
        String smoking,
        String religion,
        String residence
) {

    public static CompatibilityProfile from(User user) {
        UserProfile profile = user.getUserProfile();
        return new CompatibilityProfile(
                user.getId(),
                profile != null ? profile.getAge() : null,
                user.getDeptName(),
                user.getStudentNum(),
                profile != null ? profile.getHeight() : null,
                profile != null ? profile.getMbti() : null,
                profile != null ? profile.getSmoking() : null,
                profile != null ? profile.getReligion() : null,
                profile != null ? profile.getResidence() : null
        );
    }
}
