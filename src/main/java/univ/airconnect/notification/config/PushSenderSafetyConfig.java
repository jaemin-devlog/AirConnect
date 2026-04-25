package univ.airconnect.notification.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 운영 계열 환경에서 FCM이 비활성화된 채로 가짜 sender가 동작하지 않도록 막는다.
 */
@Component
@RequiredArgsConstructor
public class PushSenderSafetyConfig {

    private static final Set<String> STRICT_PROFILES = Set.of("prod", "production", "staging", "stage");

    private final Environment environment;

    @PostConstruct
    public void validatePushSenderConfiguration() {
        boolean strictProfile = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(STRICT_PROFILES::contains);
        boolean fcmEnabled = environment.getProperty("notification.push.fcm.enabled", Boolean.class, false);
        if (strictProfile && !fcmEnabled) {
            throw new IllegalStateException("FCM push sender must be enabled in prod/staging profiles.");
        }
    }
}
