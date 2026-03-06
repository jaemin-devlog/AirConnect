package univ.airconnect.auth.service.oauth.kakao;

import org.springframework.stereotype.Component;

import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.service.oauth.SocialAuthClient;

@Component
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
        // socialToken = 카카오 accessToken
        return kakaoApiClient.getKakaoUserId(socialToken);
    }
}