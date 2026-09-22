package univ.airconnect.auth.service.oauth.kakao;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import univ.airconnect.auth.exception.SocialApiException;

@Component
@ConditionalOnProperty(prefix = "auth.social.kakao", name = "enabled", havingValue = "true")
public class KakaoApiClient {

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoApiClient(KakaoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String getKakaoUserId(String kakaoAccessToken) {
        try {
            KakaoMeResponse response = restClient.get()
                    .uri(properties.mePath())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoMeResponse.class);

            if (response == null || response.id() == null) {
                throw new SocialApiException("Kakao /v2/user/me response is null or missing id");
            }
            return String.valueOf(response.id());
        } catch (Exception e) {
            throw new SocialApiException("Failed to call Kakao API (/v2/user/me)", e);
        }
    }

    private record KakaoMeResponse(Long id) {}
}
