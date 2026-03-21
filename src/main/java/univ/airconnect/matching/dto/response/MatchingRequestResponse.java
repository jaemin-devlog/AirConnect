package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.dto.response.UserProfileResponse;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRequestResponse {

    private Long connectionId;
    private Long userId;
    private String socialId;
    private String nickname;
    private String deptName;
    private Integer studentNum;
    private UserStatus userStatus;
    private OnboardingStatus onboardingStatus;
    private boolean profileExists;
    private boolean profileImageUploaded;
    private boolean emailVerified;
    private Integer tickets;
    private UserProfileResponse profile;
    private ConnectionStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}

