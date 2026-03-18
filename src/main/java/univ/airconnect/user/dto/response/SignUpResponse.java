package univ.airconnect.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponse {
    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("onboardingStatus")
    private String onboardingStatus;
    
    @JsonProperty("profileExists")
    private boolean profileExists;
    
    @JsonProperty("matchingQueueActive")
    private boolean matchingQueueActive;
}

