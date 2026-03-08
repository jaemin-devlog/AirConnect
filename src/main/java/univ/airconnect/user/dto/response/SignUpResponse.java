package univ.airconnect.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignUpResponse {
    private Long userId;
    private String email;
    private String name;
    private String status;
}

