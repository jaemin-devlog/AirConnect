package univ.airconnect.matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.dto.response.UserProfileResponse;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MatchingRequestResponse {

    private Long connectionId;
    private Long userId;
    private String nickname;
    private String deptName;
    private Integer admissionYear;
    private OnboardingStatus onboardingStatus;
    private boolean emailVerified;
    private boolean profileExists;
    private boolean profileImageUploaded;
    private Integer age;
    private UserProfileResponse profile;
    private ConnectionStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}

