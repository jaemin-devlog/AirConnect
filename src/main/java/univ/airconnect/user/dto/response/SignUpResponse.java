package univ.airconnect.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
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

    public SignUpResponse(Long userId,
                          String email,
                          String name,
                          String status,
                          String onboardingStatus,
                          boolean profileExists) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.status = status;
        this.onboardingStatus = onboardingStatus;
        this.profileExists = profileExists;
    }

    // Backward-compatible overload for legacy call sites that still pass matchingQueueActive.
    public SignUpResponse(Long userId,
                          String email,
                          String name,
                          String status,
                          String onboardingStatus,
                          boolean profileExists,
                          boolean ignoredMatchingQueueActive) {
        this(userId, email, name, status, onboardingStatus, profileExists);
    }
}

