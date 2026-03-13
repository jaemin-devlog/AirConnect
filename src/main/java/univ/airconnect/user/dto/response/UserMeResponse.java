package univ.airconnect.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;

@Getter
@Builder
public class UserMeResponse {

    private Long userId;
    private SocialProvider provider;
    private String socialId;
    private String email;
    private String name;
    private String deptName;
    private String nickname;
    private Integer studentNum;
    private UserStatus status;
    private OnboardingStatus onboardingStatus;
    private boolean profileExists;
    private UserProfileResponse profile;
}