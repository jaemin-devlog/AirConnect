package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.dto.response.UserProfileResponse;

@Getter
@Builder
@AllArgsConstructor
public class MatchingCandidateResponse {

    private Long userId;
    private Integer admissionYear;
    /** 모바일 구버전 호환 필드. admissionYear와 동일한 두 자리 입학연도이다. */
    private Integer studentNum;
    private OnboardingStatus onboardingStatus;
    private boolean emailVerified;
    private boolean profileExists;
    private boolean profileImageUploaded;
    private Integer age;
    private String nickname;
    private String deptName;
    private String profileImage;
    private Gender gender;
    private UserProfileResponse profile;
}

