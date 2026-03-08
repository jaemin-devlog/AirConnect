package univ.airconnect.user.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SignUpRequest {

    // accessToken은 이미 로그인에서 받았으므로, 여기서는 추가 정보만 받음
    private String accessToken;      // 로그인 후 받은 accessToken
    private String refreshToken;     // 로그인 후 받은 refreshToken
    private String deviceId;         // 디바이스 ID

    // 회원가입 추가 정보
    private String name;             // 사용자명
    private String nickname;         // 닉네임
    private String phoneNumber;      // 전화번호
}

