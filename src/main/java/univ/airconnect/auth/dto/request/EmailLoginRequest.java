package univ.airconnect.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailLoginRequest {

    @JsonProperty("verificationToken")
    private String verificationToken;

    @JsonProperty("password")
    private String password;

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("email")
    private String email;

    public EmailLoginRequest(String verificationToken, String password, String deviceId) {
        this.verificationToken = verificationToken;
        this.password = password;
        this.deviceId = deviceId;
    }

    public EmailLoginRequest(String verificationToken, String password, String deviceId, String email) {
        this.verificationToken = verificationToken;
        this.password = password;
        this.deviceId = deviceId;
        this.email = email;
    }
}
