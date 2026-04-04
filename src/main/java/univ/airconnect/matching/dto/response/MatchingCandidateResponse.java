package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.dto.response.UserProfileResponse;

@Getter
@Builder
@AllArgsConstructor
public class MatchingCandidateResponse {

    private Long userId;
    private String socialId;
    private Integer studentNum;
    private Integer age;
    private UserStatus status;
    private OnboardingStatus onboardingStatus;
    private boolean profileExists;
    private boolean profileImageUploaded;
    private boolean emailVerified;
    private Integer tickets;
    private String nickname;
    private String deptName;
    private String profileImage;
    private UserProfileResponse profile;
}

