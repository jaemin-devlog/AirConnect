package univ.airconnect.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개발/테스트 환경에서만 사용 가능한 임시 토큰 생성 요청
 * 프로덕션 환경에서는 이 엔드포인트가 비활성화됩니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestTokenRequest {
    private String deviceId;
}

