package univ.airconnect.auth.service.oauth.kakao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
@ConditionalOnProperty(prefix = "auth.social.kakao", name = "enabled", havingValue = "true")
public class KakaoConfig {
}
