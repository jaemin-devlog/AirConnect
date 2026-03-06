package univ.airconnect.auth.service.oauth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String baseUrl,
        String mePath
) {}
