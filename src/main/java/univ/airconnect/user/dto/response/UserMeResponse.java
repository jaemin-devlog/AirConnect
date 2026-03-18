package univ.airconnect.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;

@Getter
@Builder
public class UserMeResponse {

    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("provider")
    private SocialProvider provider;
    
    @JsonProperty("socialId")
    private String socialId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("deptName")
    private String deptName;
    
    @JsonProperty("nickname")
    private String nickname;
    
    @JsonProperty("studentNum")
    private Integer studentNum;
    
    @JsonProperty("status")
    private UserStatus status;
    
    @JsonProperty("onboardingStatus")
    private OnboardingStatus onboardingStatus;
    
    @JsonProperty("profileExists")
    private boolean profileExists;
    
    @JsonProperty("profileImageUploaded")
    private boolean profileImageUploaded;
    
    @JsonProperty("emailVerified")
    private boolean emailVerified;
    
    @JsonProperty("tickets")
    private Integer tickets;
    
    @JsonProperty("profile")
    private UserProfileResponse profile;
}