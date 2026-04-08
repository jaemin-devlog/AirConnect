package univ.airconnect.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.auth.domain.entity.SocialProvider;

/**
 * 소셜 로그인 요청 DTO
 *
 * 사용 예시:
 * - Apple: provider=APPLE, socialToken={Apple identityToken}
 *
 * deviceId는 앱에서 생성해서 고정 저장 (UUID 추천)
 * 멀티 디바이스 토큰 관리에 사용됨
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {
    @JsonProperty("provider")
    private SocialProvider provider;

    @JsonProperty("socialToken")
    private String socialToken;

    @JsonProperty("deviceId")
    private String deviceId;
}
