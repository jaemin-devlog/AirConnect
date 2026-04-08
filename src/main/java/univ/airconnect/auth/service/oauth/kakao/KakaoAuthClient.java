package univ.airconnect.auth.service.oauth.kakao;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.service.oauth.SocialAuthClient;

@Component
@ConditionalOnProperty(prefix = "auth.social.kakao", name = "enabled", havingValue = "true")
public class KakaoAuthClient implements SocialAuthClient {

    private final KakaoApiClient kakaoApiClient;

    public KakaoAuthClient(KakaoApiClient kakaoApiClient) {
        this.kakaoApiClient = kakaoApiClient;
    }

    @Override
    public SocialProvider supports() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String getSocialId(String socialToken) {
        // socialToken은 카카오 accessToken이다.
        return kakaoApiClient.getKakaoUserId(socialToken);
    }
}
